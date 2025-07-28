package net.ipojman.schwab.zio

import zio.*
import zio.http.*
import java.time.LocalDate

/**
 * Quick test to verify market hours endpoint is working
 */
object TestMarketHours extends ZIOAppDefault {
  
  override def run = {
    val program = for {
      _ <- Console.printLine("=== Testing Market Hours Endpoint ===\n")
      
      // Test getting market hours for EQUITY
      _ <- Console.printLine("1. Testing getMarketHours for EQUITY...")
      equityHours <- MarketDataService.getMarketHours(List("EQUITY"))
      _ <- equityHours.equity match {
        case Some(hours) =>
          Console.printLine(s"  ✅ EQUITY market hours retrieved successfully")
        case None =>
          Console.printLine(s"  ❌ No EQUITY hours returned")
      }
      
      // Test getting market hours for multiple markets
      _ <- Console.printLine("\n2. Testing getMarketHours for EQUITY and OPTION...")
      multiHours <- MarketDataService.getMarketHours(List("EQUITY", "OPTION"))
      _ <- Console.printLine(s"  Equity hours present: ${multiHours.equity.isDefined}")
      _ <- Console.printLine(s"  Option hours present: ${multiHours.option.isDefined}")
      
      // Test with a specific date
      _ <- Console.printLine("\n3. Testing with specific date...")
      tomorrow = LocalDate.now().plusDays(1)
      dateStr = tomorrow.toString
      futureHours <- MarketDataService.getMarketHours(List("EQUITY"), Some(dateStr))
      _ <- Console.printLine(s"  Market hours for $dateStr retrieved")
      
      // Test the single market method
      _ <- Console.printLine("\n4. Testing getMarketHour (single market)...")
      singleHour <- MarketDataService.getMarketHour("EQUITY")
      _ <- singleHour.equity match {
        case Some(hours) =>
          Console.printLine(s"  ✅ Single market method working")
        case None =>
          Console.printLine(s"  ❌ Single market method failed")
      }
      
      // Test direct API call
      _ <- Console.printLine("\n5. Testing direct API call...")
      _ <- (for {
        client <- ZIO.service[SchwabClient]
        seamlessClient = client.asInstanceOf[SeamlessSchwabClient]
        token <- seamlessClient.ensureAuthenticated()
        
        endpoint = s"marketdata/v1/markets?markets=EQUITY"
        _ <- Console.printLine(s"  Calling: $endpoint")
        response <- client.makeRawApiCall(endpoint, token.access_token)
        _ <- Console.printLine(s"  Response received: ${response.take(200)}...")
      } yield ()).catchAll { err =>
        Console.printLineError(s"  ❌ Direct API call failed: ${err.getMessage}")
      }
      
    } yield ()
    
    program
      .provide(
        SeamlessSchwabClient.live,
        LiveMarketDataService.layer
      )
      .tapError(err => Console.printLineError(s"Error: ${err.getMessage}"))
  }
}