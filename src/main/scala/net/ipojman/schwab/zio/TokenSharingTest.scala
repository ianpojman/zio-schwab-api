package net.ipojman.schwab.zio

import zio.*
import zio.json.*

object TokenSharingTest extends ZIOAppDefault {
  
  override def run = {
    val app = for {
      _ <- Console.printLine("=== Token Sharing Test ===")
      
      // Test 1: Check if token file exists
      tokenFile <- ZIO.attempt(new java.io.File(s"${java.lang.System.getProperty("user.home")}/.schwab_token.json"))
      exists = tokenFile.exists()
      _ <- Console.printLine(s"Token file exists: $exists")
      
      result <- if (exists) {
        for {
          // Test 2: Read and parse token
          content <- ZIO.attempt(scala.io.Source.fromFile(tokenFile).mkString).orDie
          _ <- Console.printLine(s"Token file content (first 100 chars): ${content.take(100)}...")
          
          // Test 3: Use SeamlessSchwabClient to get token
          _ <- Console.printLine("\nTesting SeamlessSchwabClient token retrieval...")
          token <- ZIO.serviceWithZIO[SchwabClient] {
            case seamless: SeamlessSchwabClient => 
              seamless.ensureAuthenticated()
                .tap(t => Console.printLine(s"Successfully retrieved token: ${t.access_token.take(20)}..."))
                .tapError(e => Console.printLine(s"Error retrieving token: ${e.getMessage}"))
            case _ => 
              ZIO.fail(new Exception("Not a SeamlessSchwabClient"))
          }
          
          // Test 4: Make a simple API call
          _ <- Console.printLine("\nTesting API call with shared token...")
          marketDataService <- ZIO.service[MarketDataService]
          quote <- marketDataService.getQuote("AAPL")
            .tap(q => Console.printLine(s"Successfully fetched quote for ${q.symbol}: $$${q.last.getOrElse("N/A")}"))
            .tapError(e => Console.printLine(s"Error fetching quote: ${e.getMessage}"))
        } yield ()
      } else {
        Console.printLine("No token file found. Please run an app that performs OAuth first.")
      }
      
    } yield result
    
    app.provide(
      SeamlessSchwabClient.live,
      LiveMarketDataService.layer,
      Scope.default
    )
  }
}