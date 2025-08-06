package net.ipojman.schwab.zio

import zio.*
import zio.logging.backend.SLF4J
import net.ipojman.schwab.zio.models.{QuoteResponse, SchwabApiResponse}

/**
 * Example showing how to use the safe API calls that return SchwabApiResponse
 * instead of throwing exceptions for API errors
 */
object SafeQuoteExample extends ZIOAppDefault {

  /**
   * Example using the direct safe API calls
   */
  val safeApiExample = for {
    _ <- ZIO.logInfo("Starting safe API example...")
    client <- ZIO.service[SchwabClient]
    
    // Using the safe API call directly - this won't throw on API errors
    _ <- ZIO.logInfo("Getting quotes using safe API calls...")
    
    // Test with invalid symbol to see error handling
    response <- client match {
      case seamless: SeamlessSchwabClient =>
        seamless.makeApiCallSafeWithRetry[Map[String, QuoteResponse]]("marketdata/v1/quotes?symbols=INVALID1,INVALID2")
      case _ =>
        for {
          token <- client.getAccessToken
          response <- client.makeApiCallSafe[Map[String, QuoteResponse]]("marketdata/v1/quotes?symbols=INVALID1,INVALID2", token.access_token)
        } yield response
    }
    
    _ <- response match {
      case SchwabApiResponse.Success(quotes) =>
        ZIO.logInfo(s"✓ Successfully got quotes for symbols: ${quotes.keys.mkString(", ")}")
        
      case SchwabApiResponse.Error(errors) =>
        ZIO.logInfo("✓ Received API errors (expected for invalid symbols):")
        ZIO.foreach(errors) { error =>
          ZIO.logInfo(s"  - Error: ${error.message}" + error.id.map(id => s" (ID: $id)").getOrElse(""))
        }
    }
    
  } yield ()

  /**
   * Example using the MarketDataService with error handling
   */
  def marketDataServiceExample = {
    def safeGetQuotes(symbols: List[String])(service: MarketDataService) = {
      service.getQuotes(symbols).either.flatMap {
        case Right(quotes) =>
          ZIO.logInfo(s"✓ Got quotes for: ${quotes.keys.mkString(", ")}")
          ZIO.succeed(Some(quotes))
        case Left(error) =>
          ZIO.logWarning(s"Could not get quotes for ${symbols.mkString(", ")}: ${error.getMessage}")
          ZIO.succeed(None)
      }
    }
    
    for {
      _ <- ZIO.logInfo("Starting MarketDataService example...")
      service <- ZIO.service[MarketDataService]
      
      // Define symbols to test
      validSymbols = List("AAPL", "MSFT")
      invalidSymbols = List("INVALID1", "INVALID2")
      
      // Test with valid symbols
      _ <- ZIO.logInfo(s"Testing with valid symbols: ${validSymbols.mkString(", ")}")
      _ <- safeGetQuotes(validSymbols)(service)
      
      // Test with invalid symbols  
      _ <- ZIO.logInfo(s"Testing with invalid symbols: ${invalidSymbols.mkString(", ")}")
      _ <- safeGetQuotes(invalidSymbols)(service)
      
      _ <- ZIO.logInfo("MarketDataService example completed.")
      
    } yield ()
  }

  val program = for {
    _ <- safeApiExample
    _ <- ZIO.logInfo("=" * 50)
    _ <- marketDataServiceExample
  } yield ()

  def run = program.provide(
    Runtime.removeDefaultLoggers >>> SLF4J.slf4j,
    SeamlessSchwabClient.live,
    LiveMarketDataService.layer
  ).exitCode
}