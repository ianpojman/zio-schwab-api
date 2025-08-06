package net.ipojman.schwab.zio

import zio.*
import zio.logging.backend.SLF4J

object QuoteErrorHandlingTest extends ZIOAppDefault {

  val program = for {
    _ <- ZIO.logInfo("Starting quote error handling test...")
    service <- ZIO.service[MarketDataService]
    
    // Test 1: Valid ticker symbols (should succeed)
    _ <- ZIO.logInfo("Test 1: Getting quotes for valid symbols (AAPL, MSFT)")
    validQuotes <- service.getQuotes(List("AAPL", "MSFT")).either
    _ <- validQuotes match {
      case Right(quotes) =>
        ZIO.logInfo(s"✓ Successfully got ${quotes.size} quotes: ${quotes.keys.mkString(", ")}")
      case Left(error) =>
        ZIO.logError(s"✗ Unexpected error for valid symbols: ${error.getMessage}")
    }
    
    // Test 2: Invalid ticker symbols (should return error)
    _ <- ZIO.logInfo("Test 2: Getting quotes for invalid symbols (INVALID1, INVALID2)")
    invalidQuotes <- service.getQuotes(List("INVALID1", "INVALID2")).either
    _ <- invalidQuotes match {
      case Right(quotes) =>
        ZIO.logWarning(s"? Got quotes for invalid symbols (unexpected): ${quotes.keys.mkString(", ")}")
      case Left(error) =>
        if (error.getMessage.contains("Schwab API error")) {
          ZIO.logInfo(s"✓ Correctly handled API error: ${error.getMessage}")
        } else {
          ZIO.logError(s"✗ Got error but not the expected API error format: ${error.getMessage}")
        }
    }
    
    // Test 3: Mix of valid and invalid symbols
    _ <- ZIO.logInfo("Test 3: Getting quotes for mixed symbols (AAPL, INVALID3)")
    mixedQuotes <- service.getQuotes(List("AAPL", "INVALID3")).either
    _ <- mixedQuotes match {
      case Right(quotes) =>
        ZIO.logInfo(s"? Got quotes for mixed symbols: ${quotes.keys.mkString(", ")} - may be valid behavior")
      case Left(error) =>
        if (error.getMessage.contains("Schwab API error")) {
          ZIO.logInfo(s"✓ Correctly handled API error for mixed symbols: ${error.getMessage}")
        } else {
          ZIO.logError(s"✗ Got error but not the expected API error format: ${error.getMessage}")
        }
    }
    
    // Test 4: Single invalid symbol using getQuote
    _ <- ZIO.logInfo("Test 4: Getting single quote for invalid symbol (INVALID4)")
    singleInvalidQuote <- service.getQuote("INVALID4").either
    _ <- singleInvalidQuote match {
      case Right(quote) =>
        ZIO.logWarning(s"? Got quote for invalid symbol (unexpected): ${quote.symbol}")
      case Left(error) =>
        if (error.getMessage.contains("Schwab API error")) {
          ZIO.logInfo(s"✓ Correctly handled API error for single invalid symbol: ${error.getMessage}")
        } else {
          ZIO.logError(s"✗ Got error but not the expected API error format: ${error.getMessage}")
        }
    }
    
    _ <- ZIO.logInfo("Quote error handling test completed.")
    
  } yield ()

  def run = program.provide(
    Runtime.removeDefaultLoggers >>> SLF4J.slf4j,
    SeamlessSchwabClient.live,
    LiveMarketDataService.layer
  ).exitCode
}