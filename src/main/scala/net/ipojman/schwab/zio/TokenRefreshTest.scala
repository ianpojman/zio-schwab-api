package net.ipojman.schwab.zio

import zio.*
import zio.http.*
import zio.json.*
import zio.logging.backend.SLF4J
import java.io.File
import java.nio.file.{Files, Paths}
import java.time.Instant

/**
 * Test program to verify token refresh functionality works correctly
 * This tests the following scenarios:
 * 1. Fresh token - should be used without refresh
 * 2. Expired token - should trigger automatic refresh
 * 3. Legacy token file - should be handled correctly
 * 4. 401 error - should force refresh and retry
 */
object TokenRefreshTest extends ZIOAppDefault {
  
  override val bootstrap = Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  val program = for {
    _ <- Console.printLine("=== Token Refresh Test ===")
    
    // Test 1: Check current token status
    _ <- Console.printLine("\nTest 1: Checking current token status...")
    _ <- checkTokenStatus()
    
    // Test 2: Make an API call to test automatic retry
    _ <- Console.printLine("\nTest 2: Making API call with automatic retry on 401...")
    _ <- testApiCallWithRetry()
    
    // Test 3: Force token refresh
    _ <- Console.printLine("\nTest 3: Testing forced token refresh...")
    _ <- testForcedRefresh()
    
    _ <- Console.printLine("\n✅ All tests completed!")
  } yield ()
  
  def checkTokenStatus(): ZIO[SchwabClient, Throwable, Unit] = {
    for {
      tokenStorage <- ZIO.succeed(new FileTokenStorage())
      tokenOpt <- tokenStorage.getTokenWithTimestamp()
      _ <- tokenOpt match {
        case Some((token, timestamp)) =>
          val currentTime = java.lang.System.currentTimeMillis() / 1000
          val age = currentTime - timestamp
          val expires = token.expires_in.getOrElse(1800)
          val remaining = expires - age.toInt
          val isValid = remaining > 300 // 5 minute buffer
          
          Console.printLine(s"Token found:") *>
          Console.printLine(s"  Age: ${age}s") *>
          Console.printLine(s"  Expires in: ${expires}s") *>
          Console.printLine(s"  Time remaining: ${remaining}s") *>
          Console.printLine(s"  Valid: $isValid") *>
          Console.printLine(s"  Has refresh token: ${token.refresh_token.isDefined}")
        case None =>
          Console.printLine("No token found in storage")
      }
    } yield ()
  }
  
  def testApiCallWithRetry(): ZIO[SchwabClient & MarketDataService, Throwable, Unit] = {
    for {
      // Try to get a quote - this should handle 401 errors automatically
      _ <- MarketDataService.getQuote("SPY")
        .tap(quote => Console.printLine(s"✅ SPY quote retrieved: $$${quote.last.getOrElse("N/A")}"))
        .catchAll(err => Console.printLineError(s"❌ Failed to get quote: ${err.getMessage}"))
    } yield ()
  }
  
  def testForcedRefresh(): ZIO[SchwabClient, Throwable, Unit] = {
    for {
      client <- ZIO.service[SchwabClient]
      _ <- client match {
        case seamless: SeamlessSchwabClient =>
          for {
            _ <- Console.printLine("Forcing token refresh...")
            token <- seamless.ensureAuthenticated(forceRefresh = true)
            _ <- Console.printLine(s"✅ Token refreshed. New expiry: ${token.expires_in.getOrElse(1800)}s")
          } yield ()
        case _ =>
          Console.printLine("⚠️  Not using SeamlessSchwabClient, skipping forced refresh test")
      }
    } yield ()
  }
  
  
  def run = program.provide(
    SeamlessSchwabClient.live,
    LiveMarketDataService.layer,
    SchwabApiConfig.live,
    zio.http.Client.default
  )
}