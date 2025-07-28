package net.ipojman.schwab.zio

import zio.*
import zio.http.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import net.ipojman.schwab.zio.models.OrderStatus

/**
 * Example demonstrating how to load 6 months of order history for a specific symbol
 */
object OrderHistoryExample extends ZIOAppDefault {
  
  override def run = {
    val program = for {
      _ <- Console.printLine("=== Schwab Order History Example - Loading 6 months of PM orders ===\n")
      
      // Calculate date range - 6 months ago to today
      today = LocalDate.now()
      sixMonthsAgo = today.minusMonths(6)
      
      _ <- Console.printLine(s"Loading PM orders from $sixMonthsAgo to $today...")
      
      // Get orders using the enhanced order service
      orderService <- ZIO.service[SchwabOrderServiceEnhanced]
      orders <- orderService.getOrdersForSymbol(
        symbol = "PM",
        fromDate = sixMonthsAgo,
        toDate = today
      )
      
      _ <- Console.printLine(s"\nFound ${orders.size} PM orders in the past 6 months")
      
      // Analyze orders by status
      ordersByStatus = orders.groupBy(_.status)
      _ <- Console.printLine("\nOrders by status:")
      _ <- ZIO.foreach(ordersByStatus.toList.sortBy(_._1.toString)) { case (status, statusOrders) =>
        Console.printLine(s"  $status: ${statusOrders.size} orders")
      }
      
      // Show filled orders
      filledOrders = orders.filter(_.status == OrderStatus.Filled)
      _ <- Console.printLine(s"\nFilled orders (${filledOrders.size} total):")
      _ <- ZIO.foreach(filledOrders.take(10)) { order =>
        val orderType = order.orderType
        val quantity = order.quantity
        val instruction = order.instruction
        val executionTime = order.filledTime.getOrElse(order.enteredTime)
        val price = order.price.getOrElse(0.0)
        
        Console.printLine(
          f"  $executionTime - $instruction $quantity%.0f shares @ $$${price}%.2f ($orderType)"
        )
      }
      
      _ <- ZIO.when(filledOrders.size > 10) {
        Console.printLine(s"  ... and ${filledOrders.size - 10} more filled orders")
      }
      
      // Calculate some basic metrics
      _ <- Console.printLine("\n=== PM Trading Metrics (Past 6 Months) ===")
      
      buyOrders = filledOrders.filter(_.instruction == "BUY")
      sellOrders = filledOrders.filter(_.instruction == "SELL")
      
      totalBuyQuantity = buyOrders.map(_.quantity).sum
      totalSellQuantity = sellOrders.map(_.quantity).sum
      
      _ <- Console.printLine(f"Total Buy Orders: ${buyOrders.size} (${totalBuyQuantity}%.0f shares)")
      _ <- Console.printLine(f"Total Sell Orders: ${sellOrders.size} (${totalSellQuantity}%.0f shares)")
      _ <- Console.printLine(f"Net Position Change: ${totalBuyQuantity - totalSellQuantity}%.0f shares")
      
      // Show orders by month
      _ <- Console.printLine("\nOrders by month:")
      ordersByMonth = orders
        .groupBy(order => order.enteredTime.toLocalDate.toString.substring(0, 7)) // YYYY-MM
        .toList
        .sortBy(_._1)
      
      _ <- ZIO.foreach(ordersByMonth) { case (month, monthOrders) =>
        val filledCount = monthOrders.count(_.status == OrderStatus.Filled)
        Console.printLine(f"  $month: ${monthOrders.size}%3d total orders ($filledCount filled)")
      }
      
    } yield ()
    
    program
      .provide(
        SeamlessSchwabClient.live,
        SchwabOrderServiceEnhanced.layer
      )
      .tapError(err => Console.printLineError(s"Error: ${err.getMessage}"))
  }
}