package net.ipojman.schwab.zio

import zio.*
import zio.console.*
import zio.json.*

/**
 * Example usage of the MarketDataService
 */
object MarketDataExample extends ZIOAppDefault {
  
  // Example program that demonstrates various market data API calls
  val program = for {
    _ <- Console.printLine("=== Schwab Market Data API Example ===")
    
    // Get quotes for multiple symbols
    _ <- Console.printLine("\n1. Getting quotes for AAPL, MSFT, GOOGL...")
    quotes <- MarketDataService.getQuotes(List("AAPL", "MSFT", "GOOGL"))
    _ <- ZIO.foreach(quotes) { case (symbol, quote) =>
      Console.printLine(s"$symbol: ${quote.last} (${quote.change} ${quote.percentChange}%)")
    }
    
    // Get single quote with extended fields
    _ <- Console.printLine("\n2. Getting detailed quote for SPY...")
    spy <- MarketDataService.getQuote("SPY", Some("quote,fundamental"))
    _ <- Console.printLine(s"SPY: ${spy.last} | Volume: ${spy.volume} | 52w High: ${spy.fiftyTwoWeekHigh}")
    
    // Get option chain
    _ <- Console.printLine("\n3. Getting option chain for AAPL...")
    chain <- MarketDataService.getOptionChain(
      symbol = "AAPL",
      contractType = Some("ALL"),
      strikeCount = Some(5),
      includeQuotes = true
    )
    _ <- Console.printLine(s"AAPL Options - Underlying: ${chain.underlyingPrice}, IV: ${chain.volatility}")
    
    // Get price history
    _ <- Console.printLine("\n4. Getting price history for TSLA...")
    history <- MarketDataService.getPriceHistory(
      symbol = "TSLA",
      periodType = Some("day"),
      period = Some(5),
      frequencyType = Some("minute"),
      frequency = Some(5)
    )
    _ <- Console.printLine(s"TSLA Price History - ${history.candles.size} candles")
    _ <- ZIO.foreach(history.candles.take(3)) { candle =>
      Console.printLine(s"  ${new java.util.Date(candle.datetime)}: O=${candle.open} H=${candle.high} L=${candle.low} C=${candle.close}")
    }
    
    // Get market movers
    _ <- Console.printLine("\n5. Getting top movers for S&P 500...")
    movers <- MarketDataService.getMovers("$SPX", Some("PERCENT_CHANGE_UP"))
    _ <- ZIO.foreach(movers.take(5)) { mover =>
      Console.printLine(s"  ${mover.symbol}: ${mover.percentChange}% (${mover.change})")
    }
    
    // Get market hours
    _ <- Console.printLine("\n6. Getting market hours for EQUITY and OPTION...")
    hours <- MarketDataService.getMarketHours(List("EQUITY", "OPTION"))
    _ <- ZIO.foreach(hours) { case (market, marketHours) =>
      Console.printLine(s"  $market: ${if (marketHours.isOpen) "OPEN" else "CLOSED"}")
    }
    
    // Search for instruments
    _ <- Console.printLine("\n7. Searching for Apple instruments...")
    instruments <- MarketDataService.getInstruments("AAPL", "symbol-search")
    _ <- ZIO.foreach(instruments.take(3)) { instrument =>
      Console.printLine(s"  ${instrument.symbol}: ${instrument.description} (${instrument.assetType})")
    }
    
  } yield ()
  
  // Run the program with all necessary layers
  def run = program
    .provide(
      LiveMarketDataService.layer,
      SchwabClient.seamless
    )
    .tapError(err => Console.printLineError(s"Error: ${err.getMessage}"))
}