package net.ipojman.schwab.zio

import zio.*
import zio.json.*
import java.time.ZonedDateTime
import net.ipojman.schwab.zio.models.OrderStatus

object IntegrationTests extends ZIOAppDefault {
  
  // Example program that demonstrates various market data API calls
  val program = for {
    _ <- Console.printLine("=== Schwab Market Data API Example ===")
    
    // TEST: User Preferences Endpoint
    _ <- Console.printLine("\nTEST: Checking getUserPreference endpoint...")
    _ <- (for {
      client <- ZIO.service[SchwabClient]
      seamlessClient = client.asInstanceOf[SeamlessSchwabClient]
      token <- seamlessClient.ensureAuthenticated()
      preferences <- SchwabClient.getUserPreference(token.access_token)
      _ <- Console.printLine(s"✅ User preferences retrieved successfully!")
      _ <- Console.printLine(s"Preferences: ${preferences.take(200)}...")
    } yield ()).catchAll { err =>
      Console.printLineError(s"❌ Failed to get user preferences: ${err.getMessage}")
    }
    
    // NEW SCENARIO: Quick Portfolio Check
    _ <- Console.printLine("\n0. Quick Portfolio Check - Major ETFs and Tech Stocks...")
    portfolioSymbols = List("SPY", "QQQ", "IWM", "DIA", "AAPL", "MSFT", "NVDA", "TSLA")
    portfolioQuotes <- MarketDataService.getQuotes(portfolioSymbols)
    _ <- Console.printLine("Symbol | Last Price | Change | % Change | Volume")
    _ <- Console.printLine("-" * 60)
    _ <- ZIO.foreach(portfolioSymbols) { symbol =>
      portfolioQuotes.get(symbol) match {
        case Some(quote) =>
          val last = quote.last.map(_.toString).getOrElse("N/A")
          val change = quote.change.map(c => f"$c%.2f").getOrElse("N/A")
          val pctChange = quote.percentChange.map(p => f"$p%.2f%%").getOrElse("N/A")
          val volume = quote.volume.map(v => f"$v%,d").getOrElse("N/A")
          Console.printLine(f"$symbol%-6s | $last%-10s | $change%-7s | $pctChange%-8s | $volume")
        case None =>
          Console.printLine(f"$symbol%-6s | No data available")
      }
    }
    
    // Get quotes for multiple symbols
    _ <- Console.printLine("\n1. Getting quotes for AAPL, MSFT, GOOGL...")
    quotes <- MarketDataService.getQuotes(List("AAPL", "MSFT", "GOOGL"))
    _ <- ZIO.foreach(quotes.toList) { case (symbol, quote) =>
      Console.printLine(s"$symbol: ${quote.last.getOrElse("N/A")} (${quote.change.getOrElse(0.0)} ${quote.percentChange.getOrElse(0.0)}%)")
    }
    
    // Get single quote with extended fields
    _ <- Console.printLine("\n2. Getting detailed quote for SPY...")
    spy <- MarketDataService.getQuote("SPY", Some("quote,fundamental"))
    _ <- Console.printLine(s"SPY: ${spy.last.getOrElse("N/A")} | Volume: ${spy.volume.getOrElse(0)} | 52w High: ${spy.fiftyTwoWeekHigh.getOrElse("N/A")}")
    
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
    
    // Test different minute frequencies
    _ <- Console.printLine("\n4b. Testing valid minute frequencies for Schwab API...")
    validFrequencies = List(1, 5, 10, 15, 30)
    _ <- ZIO.foreach(validFrequencies) { freq =>
      MarketDataService.getPriceHistory(
        symbol = "SPY",
        periodType = Some("day"),
        period = Some(1),
        frequencyType = Some("minute"),
        frequency = Some(freq)
      ).foldZIO(
        err => Console.printLine(s"  ❌ ${freq}-minute frequency: FAILED - ${err.getMessage}"),
        history => Console.printLine(s"  ✅ ${freq}-minute frequency: SUCCESS - ${history.candles.size} candles")
      )
    }
    
    // Test invalid 60-minute frequency (should fail)
    _ <- Console.printLine("\n  Testing invalid 60-minute frequency...")
    _ <- MarketDataService.getPriceHistory(
      symbol = "SPY",
      periodType = Some("day"),
      period = Some(1),
      frequencyType = Some("minute"),
      frequency = Some(60)
    ).foldZIO(
      err => Console.printLine(s"  ✅ 60-minute frequency correctly failed: ${err.getMessage.take(150)}..."),
      history => Console.printLine(s"  ❌ 60-minute frequency unexpectedly succeeded with ${history.candles.size} candles")
    )
    
    // Get market movers
    _ <- Console.printLine("\n5. Getting top movers for S&P 500...")
    movers <- MarketDataService.getMovers("$SPX", Some("PERCENT_CHANGE_UP"))
    _ <- ZIO.foreach(movers.take(5)) { mover =>
      Console.printLine(s"  ${mover.symbol}: ${mover.netPercentChange.orElse(mover.percentChange).getOrElse(0.0)}% (${mover.netChange.orElse(mover.change).getOrElse(0.0)})")
    }
    
    // Get market hours
    _ <- Console.printLine("\n6. Getting market hours for EQUITY and OPTION...")
    hours <- MarketDataService.getMarketHours(List("EQUITY", "OPTION"))
    _ <- hours.equity match {
      case Some(equityHours) => 
        ZIO.foreach(equityHours.toList) { case (product, details) =>
          Console.printLine(s"  Equity $product: ${if (details.isOpen) "OPEN" else "CLOSED"}")
        }
      case None => ZIO.unit
    }
    _ <- hours.option match {
      case Some(optionHours) =>
        ZIO.foreach(optionHours.toList) { case (product, details) =>
          Console.printLine(s"  Option $product: ${if (details.isOpen) "OPEN" else "CLOSED"}")
        }
      case None => ZIO.unit
    }
    
    // NEW TEST: Direct market hours API call using SchwabClient
    _ <- Console.printLine("\n6b. Testing direct market hours API call using SchwabClient...")
    _ <- (for {
      client <- ZIO.service[SchwabClient]
      seamlessClient = client.asInstanceOf[SeamlessSchwabClient]
      token <- seamlessClient.ensureAuthenticated()
      
      // Test with today's date
      today = java.time.LocalDate.now()
      dateStr = today.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
      endpoint = s"marketdata/v1/markets?markets=EQUITY&date=$dateStr"
      
      _ <- Console.printLine(s"  Calling endpoint: $endpoint")
      response <- client.makeRawApiCall(endpoint, token.access_token)
      _ <- Console.printLine(s"  Raw response: ${response.take(500)}...")
      
      // Try to parse the response
      _ <- ZIO.attempt {
        import zio.json.*
        val parsed = response.fromJson[Map[String, zio.json.ast.Json]]
        Console.printLine(s"  Parsed successfully: ${parsed}")
      }.catchAll { err =>
        Console.printLine(s"  Failed to parse as Map: ${err.getMessage}")
      }
      
      
    } yield ()).catchAll { err =>
      Console.printLineError(s"❌ Failed to test market hours endpoint: ${err.getMessage}")
    }
    
    // Search for instruments
    _ <- Console.printLine("\n7. Searching for Apple instruments...")
    instruments <- MarketDataService.getInstruments("AAPL", "symbol-search")
    _ <- ZIO.foreach(instruments.take(3)) { instrument =>
      Console.printLine(s"  ${instrument.symbol}: ${instrument.description} (${instrument.assetType})")
    }
    
    // Test Order Service functionality
    _ <- Console.printLine("\n=== Testing Order Service ===")
    
    // Define date range for orders (last 7 days)
    now = ZonedDateTime.now()
    sevenDaysAgo = now.minusDays(7)
    
    // Get all orders from the past 7 days
    _ <- Console.printLine("\n8. Getting all orders from the past 7 days...")
    allOrders <- OrderService.getOrders(
      fromEnteredTime = sevenDaysAgo,
      toEnteredTime = now,
      maxResults = Some(10)
    ).catchAll { err =>
      Console.printLineError(s"Failed to get orders: ${err.getMessage}") *>
      ZIO.succeed(List.empty)
    }
    _ <- Console.printLine(s"Found ${allOrders.size} orders")
    _ <- ZIO.foreach(allOrders.take(5)) { order =>
      Console.printLine(s"  Order ${order.orderId}: ${order.orderLegCollection.headOption.map(_.instrument.symbol).getOrElse("N/A")} - ${order.status} (${order.quantity} @ ${order.price.getOrElse("MKT")})")
    }
    
    // Get filled orders only
    _ <- Console.printLine("\n9. Getting filled orders from the past 7 days...")
    filledOrders <- OrderService.getOrders(
      fromEnteredTime = sevenDaysAgo,
      toEnteredTime = now,
      status = Some(OrderStatus.Filled),
      maxResults = Some(10)
    ).catchAll { err =>
      Console.printLineError(s"Failed to get filled orders: ${err.getMessage}") *>
      ZIO.succeed(List.empty)
    }
    _ <- Console.printLine(s"Found ${filledOrders.size} filled orders")
    _ <- ZIO.foreach(filledOrders.take(3)) { order =>
      Console.printLine(s"  Filled Order ${order.orderId}: ${order.orderLegCollection.headOption.map(_.instrument.symbol).getOrElse("N/A")} - ${order.filledQuantity} @ avg price")
    }
    
    // Get open/working orders
    _ <- Console.printLine("\n10. Getting open/working orders...")
    workingOrders <- OrderService.getOrders(
      fromEnteredTime = sevenDaysAgo,
      toEnteredTime = now,
      status = Some(OrderStatus.Working),
      maxResults = Some(10)
    ).catchAll { err =>
      Console.printLineError(s"Failed to get working orders: ${err.getMessage}") *>
      ZIO.succeed(List.empty)
    }
    _ <- Console.printLine(s"Found ${workingOrders.size} working orders")
    _ <- ZIO.foreach(workingOrders) { order =>
      Console.printLine(s"  Working Order ${order.orderId}: ${order.orderLegCollection.headOption.map(_.instrument.symbol).getOrElse("N/A")} - ${order.remainingQuantity} remaining")
    }
    
    // Historical Order Analysis Example - For Trade Performance Analysis
    _ <- Console.printLine("\n=== Historical Order Analysis for Trade Performance ===")
    
    // Get longer history for performance analysis (30 days)
    thirtyDaysAgo = now.minusDays(30)
    
    _ <- Console.printLine("\n11. Analyzing trade performance for AAPL over the past 30 days...")
    allHistoricalOrders <- OrderService.getOrders(
      fromEnteredTime = thirtyDaysAgo,
      toEnteredTime = now,
      maxResults = Some(100)
    ).catchAll { err =>
      Console.printLineError(s"Failed to get historical orders: ${err.getMessage}") *>
      ZIO.succeed(List.empty)
    }
    
    // Filter orders for specific instrument (AAPL)
    aaplOrders = allHistoricalOrders.filter(order => 
      order.orderLegCollection.exists(_.instrument.symbol == "AAPL")
    )
    _ <- Console.printLine(s"Found ${aaplOrders.size} AAPL orders in the past 30 days")
    
    // Separate buy and sell orders for AAPL
    aaplBuyOrders = aaplOrders.filter(order =>
      order.orderLegCollection.exists(leg => 
        leg.instrument.symbol == "AAPL" && leg.instruction.contains("BUY")
      )
    )
    aaplSellOrders = aaplOrders.filter(order =>
      order.orderLegCollection.exists(leg => 
        leg.instrument.symbol == "AAPL" && leg.instruction.contains("SELL")
      )
    )
    
    _ <- Console.printLine(s"  AAPL Buy Orders: ${aaplBuyOrders.size}")
    _ <- Console.printLine(s"  AAPL Sell Orders: ${aaplSellOrders.size}")
    
    // Show filled AAPL trades with execution details
    filledAaplOrders = aaplOrders.filter(_.status == OrderStatus.Filled)
    _ <- Console.printLine(s"\nFilled AAPL Trades (${filledAaplOrders.size}):")
    _ <- ZIO.foreach(filledAaplOrders.take(5)) { order =>
      val symbol = order.orderLegCollection.headOption.map(_.instrument.symbol).getOrElse("N/A")
      val instruction = order.orderLegCollection.headOption.map(_.instruction).getOrElse("N/A")
      val avgPrice = order.orderActivityCollection.flatMap(_.headOption).flatMap(_.executionLegs).flatMap(_.headOption).map(_.price)
      val priceStr = avgPrice.map(p => f"@ $$${p}%.2f").getOrElse("N/A")
      Console.printLine(s"  ${order.enteredTime.toLocalDate} - ${instruction} ${order.filledQuantity} ${symbol} ${priceStr}")
    }
    
    // Calculate basic performance metrics for AAPL
    _ <- Console.printLine("\n12. Basic AAPL Performance Metrics:")
    totalBuyQuantity = aaplBuyOrders.filter(_.status == OrderStatus.Filled).map(_.filledQuantity).sum
    totalSellQuantity = aaplSellOrders.filter(_.status == OrderStatus.Filled).map(_.filledQuantity).sum
    netPosition = totalBuyQuantity - totalSellQuantity
    
    _ <- Console.printLine(f"  Total AAPL Bought: ${totalBuyQuantity}%.0f shares")
    _ <- Console.printLine(f"  Total AAPL Sold: ${totalSellQuantity}%.0f shares") 
    _ <- Console.printLine(f"  Net Position: ${netPosition}%.0f shares")
    
    // Show all order statuses for historical analysis
    _ <- Console.printLine("\n13. Order Status Distribution (All Instruments):")
    statusCounts = allHistoricalOrders.groupBy(_.status).view.mapValues(_.size).toMap
    _ <- ZIO.foreach(statusCounts.toList.sortBy(-_._2)) { case (status, count) =>
      Console.printLine(s"  ${status}: ${count} orders")
    }
    
    // Enhanced Order Service Examples
    _ <- Console.printLine("\n=== Enhanced Order Service Examples ===")
    
    // Get enhanced service instance
    enhancedOrderService <- ZIO.service[SchwabOrderServiceEnhanced]
    
    // Example 14: Get orders for a specific symbol with enhanced parsing
    _ <- Console.printLine("\n14. Using Enhanced Order Service for symbol-specific queries...")
    spyOrders <- enhancedOrderService.getOrdersForSymbol("SPY", thirtyDaysAgo.toLocalDate, now.toLocalDate)
      .catchAll { err =>
        Console.printLineError(s"Failed to get SPY orders: ${err.getMessage}") *>
        ZIO.succeed(List.empty)
      }
    _ <- Console.printLine(s"Found ${spyOrders.size} SPY orders (with fixed childOrderStrategies parsing)")
    
    // Example 15: Get filled orders with enhanced service
    _ <- Console.printLine("\n15. Getting filled orders for QQQ...")
    qqqFilledOrders <- enhancedOrderService.getFilledOrdersForSymbol("QQQ", thirtyDaysAgo.toLocalDate, now.toLocalDate)
      .catchAll { err =>
        Console.printLineError(s"Failed to get QQQ filled orders: ${err.getMessage}") *>
        ZIO.succeed(List.empty)
      }
    _ <- Console.printLine(s"Found ${qqqFilledOrders.size} filled QQQ orders")
    _ <- ZIO.foreach(qqqFilledOrders.take(3)) { order =>
      val priceStr = order.price.map(p => f"@ $$${p}%.2f").getOrElse("@ MKT")
      Console.printLine(s"  ${order.enteredTime.toLocalDate} - ${order.instruction} ${order.quantity} shares ${priceStr}")
    }
    
    // Example 16: Get all orders across all symbols
    _ <- Console.printLine("\n16. Getting all orders across all symbols for the past 7 days...")
    allSymbolOrders <- enhancedOrderService.getOrdersForAllSymbols(sevenDaysAgo.toLocalDate, now.toLocalDate)
      .catchAll { err =>
        Console.printLineError(s"Failed to get all symbol orders: ${err.getMessage}") *>
        ZIO.succeed(List.empty)
      }
    _ <- Console.printLine(s"Found ${allSymbolOrders.size} total orders across all symbols")
    
    // Show top 5 most active symbols
    symbolActivity = allSymbolOrders.groupBy(_.symbol).view.mapValues(_.size).toList.sortBy(-_._2).take(5)
    _ <- Console.printLine("Top 5 most active symbols:")
    _ <- ZIO.foreach(symbolActivity) { case (symbol, count) =>
      Console.printLine(s"  ${symbol}: ${count} orders")
    }
    
  } yield ()
  
  // Run the program with all necessary layers
  def run = program
    .provide(
      LiveMarketDataService.layer,
      LiveOrderService.layer,
      SchwabOrderServiceEnhanced.layer,
      SchwabClient.seamless
    )
    .tapError(err => Console.printLineError(s"Error: ${err.getMessage}"))
}