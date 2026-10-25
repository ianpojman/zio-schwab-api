package net.ipojman.schwab.zio

import zio.*
import zio.test.*
import zio.test.Assertion.*
import java.time.LocalDate
import net.ipojman.schwab.zio.models.OrderStatus

object OrderHistorySpec extends ZIOSpecDefault {
  
  // These tests call the live broker and need a signed-in token, so the suite is ignored in the default run.
  def spec = suite("OrderHistorySpec")(
    test("load 6 months of sample-symbol orders") {
      for {
        // Calculate date range
        today <- ZIO.succeed(LocalDate.now())
        sixMonthsAgo <- ZIO.succeed(today.minusMonths(6))
        
        // Get orders
        orderService <- ZIO.service[SchwabOrderServiceEnhanced]
        orders <- orderService.getOrdersForSymbol(
          symbol = "XYZ",
          fromDate = sixMonthsAgo,
          toDate = today
        )
        
        // Basic assertions
        _ <- Console.printLine(s"Found ${orders.size} sample-symbol orders in the past 6 months")
        
        // Group by status for analysis
        ordersByStatus = orders.groupBy(_.status)
        _ <- ZIO.foreach(ordersByStatus.toList) { case (status, statusOrders) =>
          Console.printLine(s"  $status: ${statusOrders.size} orders")
        }
        
        // Additional metrics
        filledOrders = orders.filter(_.status == OrderStatus.Filled)
        buyOrders = filledOrders.filter(_.instruction == "BUY")
        sellOrders = filledOrders.filter(_.instruction == "SELL")
        
        _ <- Console.printLine(s"  Filled: ${filledOrders.size}")
        _ <- Console.printLine(s"  Buy orders: ${buyOrders.size}")
        _ <- Console.printLine(s"  Sell orders: ${sellOrders.size}")
        
      } yield {
        // Assert that we can successfully retrieve orders
        assert(orders)(isNonEmpty) && 
        assert(orders.forall(_.symbol.contains("XYZ")))(isTrue)
      }
    }.provide(
      SeamlessSchwabClient.live,
      SchwabOrderServiceEnhanced.layer
    ) @@ TestAspect.timeout(60.seconds),
    
    test("verify order date range filtering") {
      for {
        // Test with a specific date range
        endDate <- ZIO.succeed(LocalDate.now())
        startDate <- ZIO.succeed(endDate.minusMonths(6))
        
        orderService <- ZIO.service[SchwabOrderServiceEnhanced]
        orders <- orderService.getOrdersForSymbol(
          symbol = "XYZ",
          fromDate = startDate,
          toDate = endDate
        )
        
        // Verify all orders are within the date range
        orderDates = orders.map(order => order.enteredTime.toLocalDate)
        
      } yield {
        assert(orderDates.forall(date => 
          !date.isBefore(startDate) && !date.isAfter(endDate)
        ))(isTrue)
      }
    }.provide(
      SeamlessSchwabClient.live,
      SchwabOrderServiceEnhanced.layer
    ) @@ TestAspect.timeout(60.seconds),
    
    test("analyze sample-symbol trading metrics") {
      for {
        today <- ZIO.succeed(LocalDate.now())
        sixMonthsAgo <- ZIO.succeed(today.minusMonths(6))
        
        orders <- ZIO.serviceWithZIO[SchwabOrderServiceEnhanced](_.getOrdersForSymbol(
          symbol = "XYZ",
          fromDate = sixMonthsAgo,
          toDate = today
        ))
        
        filledOrders = orders.filter(_.status == OrderStatus.Filled)
        
        // Calculate total quantities
        totalBuyQuantity = filledOrders
          .filter(_.instruction == "BUY")
          .map(_.quantity)
          .sum
          
        totalSellQuantity = filledOrders
          .filter(_.instruction == "SELL")
          .map(_.quantity)
          .sum
        
        netPosition = totalBuyQuantity - totalSellQuantity
        
        _ <- Console.printLine(f"XYZ Trading Summary (6 months):")
        _ <- Console.printLine(f"  Total bought: ${totalBuyQuantity}%.0f shares")
        _ <- Console.printLine(f"  Total sold: ${totalSellQuantity}%.0f shares")
        _ <- Console.printLine(f"  Net position change: ${netPosition}%.0f shares")
        
      } yield {
        // Verify calculations are non-negative
        assert(totalBuyQuantity)(isGreaterThanEqualTo(0.0)) &&
        assert(totalSellQuantity)(isGreaterThanEqualTo(0.0))
      }
    }.provide(
      SeamlessSchwabClient.live,
      SchwabOrderServiceEnhanced.layer
    ) @@ TestAspect.timeout(60.seconds)
  ) @@ TestAspect.ignore
}