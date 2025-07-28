package net.ipojman.schwab.zio

import zio.*
import java.time.LocalDate
import net.ipojman.schwab.zio.models.OrderStatus

/**
 * Example program demonstrating the enhanced order service functionality
 */
object OrderServiceEnhancedExample extends ZIOAppDefault {
  
  val program = for {
    _ <- Console.printLine("=== Schwab Enhanced Order Service Example ===")
    
    // Get enhanced order service
    orderService <- ZIO.service[SchwabOrderServiceEnhanced]
    
    // Define date range
    today = LocalDate.now()
    thirtyDaysAgo = today.minusDays(30)
    sevenDaysAgo = today.minusDays(7)
    
    // Example 1: Get all orders for a specific symbol (e.g., AAPL)
    _ <- Console.printLine("\n1. Getting AAPL orders from the past 7 days...")
    aaplOrders <- orderService.getOrdersForSymbol("AAPL", sevenDaysAgo, today)
      .catchAll { err =>
        Console.printLineError(s"Failed to get AAPL orders: ${err.getMessage}") *>
        ZIO.succeed(List.empty)
      }
    
    _ <- Console.printLine(s"Found ${aaplOrders.size} AAPL orders")
    _ <- ZIO.foreach(aaplOrders.take(5)) { order =>
      val priceStr = order.price.map(p => f"@ $$${p}%.2f").getOrElse("@ MKT")
      Console.printLine(f"  Order ${order.orderId}: ${order.instruction} ${order.quantity}%.0f shares ${priceStr} - ${order.status}")
    }
    
    // Example 2: Get only filled orders for a symbol
    _ <- Console.printLine("\n2. Getting filled MSFT orders from the past 30 days...")
    msftFilledOrders <- orderService.getFilledOrdersForSymbol("MSFT", thirtyDaysAgo, today)
      .catchAll { err =>
        Console.printLineError(s"Failed to get MSFT filled orders: ${err.getMessage}") *>
        ZIO.succeed(List.empty)
      }
    
    _ <- Console.printLine(s"Found ${msftFilledOrders.size} filled MSFT orders")
    _ <- ZIO.foreach(msftFilledOrders.take(3)) { order =>
      val priceStr = order.price.map(p => f"@ $$${p}%.2f").getOrElse("@ MKT")
      val filledDate = order.filledTime.map(_.toLocalDate.toString).getOrElse("N/A")
      Console.printLine(f"  Filled on ${filledDate}: ${order.instruction} ${order.quantity}%.0f shares ${priceStr}")
    }
    
    // Example 3: Get orders for all symbols
    _ <- Console.printLine("\n3. Getting all orders from the past 7 days...")
    allOrders <- orderService.getOrdersForAllSymbols(sevenDaysAgo, today)
      .catchAll { err =>
        Console.printLineError(s"Failed to get all orders: ${err.getMessage}") *>
        ZIO.succeed(List.empty)
      }
    
    _ <- Console.printLine(s"Found ${allOrders.size} total orders")
    
    // Group by symbol to show order distribution
    ordersBySymbol = allOrders.groupBy(_.symbol)
    _ <- Console.printLine(s"\nOrder distribution by symbol:")
    _ <- ZIO.foreach(ordersBySymbol.toList.sortBy(-_._2.size).take(10)) { case (symbol, orders) =>
      val filledCount = orders.count(_.status == OrderStatus.Filled)
      val workingCount = orders.count(_.status == OrderStatus.Working)
      val canceledCount = orders.count(_.status == OrderStatus.Canceled)
      Console.printLine(s"  ${symbol}: ${orders.size} orders (${filledCount} filled, ${workingCount} working, ${canceledCount} canceled)")
    }
    
    // Example 4: Calculate basic trading metrics
    _ <- Console.printLine("\n4. Trading metrics for the past 30 days...")
    thirtyDayOrders <- orderService.getOrdersForAllSymbols(thirtyDaysAgo, today)
      .catchAll { err =>
        Console.printLineError(s"Failed to get 30-day orders: ${err.getMessage}") *>
        ZIO.succeed(List.empty)
      }
    
    // Calculate metrics
    totalOrders = thirtyDayOrders.size
    filledOrders = thirtyDayOrders.filter(_.status == OrderStatus.Filled)
    filledCount = filledOrders.size
    fillRate = if (totalOrders > 0) (filledCount.toDouble / totalOrders * 100) else 0.0
    
    buyOrders = filledOrders.filter(_.instruction.contains("BUY"))
    sellOrders = filledOrders.filter(_.instruction.contains("SELL"))
    
    _ <- Console.printLine(f"  Total orders: ${totalOrders}")
    _ <- Console.printLine(f"  Filled orders: ${filledCount} (${fillRate}%.1f%% fill rate)")
    _ <- Console.printLine(f"  Buy orders: ${buyOrders.size}")
    _ <- Console.printLine(f"  Sell orders: ${sellOrders.size}")
    
    // Show most traded symbols
    _ <- Console.printLine("\n5. Most traded symbols (by order count):")
    mostTraded = filledOrders.groupBy(_.symbol).view.mapValues(_.size).toList.sortBy(-_._2).take(5)
    _ <- ZIO.foreach(mostTraded) { case (symbol, count) =>
      Console.printLine(s"  ${symbol}: ${count} filled orders")
    }
    
    // Example 6: Analyze a specific trading day
    _ <- Console.printLine("\n6. Trading activity for today...")
    todayOrders <- orderService.getOrdersForAllSymbols(today, today)
      .catchAll { err =>
        Console.printLineError(s"Failed to get today's orders: ${err.getMessage}") *>
        ZIO.succeed(List.empty)
      }
    
    _ <- Console.printLine(s"Today's orders: ${todayOrders.size}")
    todaysByStatus = todayOrders.groupBy(_.status).view.mapValues(_.size).toMap
    _ <- ZIO.foreach(todaysByStatus.toList.sortBy(_._1.toString)) { case (status, count) =>
      Console.printLine(s"  ${status}: ${count}")
    }
    
    // Show today's trades with details
    _ <- if (todayOrders.nonEmpty) {
      for {
        _ <- Console.printLine("\nToday's orders (up to 10):")
        _ <- ZIO.foreach(todayOrders.take(10)) { order =>
          val priceStr = order.price.map(p => f"@ $$${p}%.2f").getOrElse("@ MKT")
          val timeStr = order.enteredTime.toLocalTime.toString
          Console.printLine(f"  ${timeStr} - ${order.symbol}: ${order.instruction} ${order.quantity}%.0f ${priceStr} - ${order.status}")
        }
      } yield ()
    } else ZIO.unit
    
  } yield ()
  
  // Run the program with all necessary layers
  def run = program
    .provide(
      SchwabOrderServiceEnhanced.layer,
      SchwabClient.seamless
    )
    .tapError(err => Console.printLineError(s"Program error: ${err.getMessage}"))
}