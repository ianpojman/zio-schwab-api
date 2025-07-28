package net.ipojman.schwab.zio

import zio.*
import zio.test.*
import zio.test.Assertion.*
import java.time.LocalDate
import net.ipojman.schwab.zio.models.OrderStatus

object OrderHistorySpec extends ZIOSpecDefault {
  
  def spec = suite("OrderHistorySpec")(
    test("load 6 months of PM orders") {
      for {
        // Calculate date range
        today <- ZIO.succeed(LocalDate.now())
        sixMonthsAgo <- ZIO.succeed(today.minusMonths(6))
        
        // Get orders
        orderService <- ZIO.service[SchwabOrderServiceEnhanced]
        orders <- orderService.getOrdersForSymbol(
          symbol = "PM",
          fromDate = sixMonthsAgo,
          toDate = today
        )
        
        // Basic assertions
        _ <- TestConsole.printLine(s"Found ${orders.size} PM orders in the past 6 months")
        
        // Group by status for analysis
        ordersByStatus = orders.groupBy(_.status)
        _ <- ZIO.foreach(ordersByStatus.toList) { case (status, statusOrders) =>
          TestConsole.printLine(s"  $status: ${statusOrders.size} orders")
        }
        
        // Additional metrics
        filledOrders = orders.filter(_.status == OrderStatus.Filled)
        buyOrders = filledOrders.filter(_.instruction == "BUY")
        sellOrders = filledOrders.filter(_.instruction == "SELL")
        
        _ <- TestConsole.printLine(s"  Filled: ${filledOrders.size}")
        _ <- TestConsole.printLine(s"  Buy orders: ${buyOrders.size}")
        _ <- TestConsole.printLine(s"  Sell orders: ${sellOrders.size}")
        
      } yield {
        // Assert that we can successfully retrieve orders
        assert(orders)(isNonEmpty) && 
        assert(orders.forall(_.symbol.contains("PM")))(isTrue)
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
          symbol = "PM",
          fromDate = startDate,
          toDate = endDate
        )
        
        // Verify all orders are within the date range
        orderDates = orders.map(order => LocalDate.parse(order.enteredTime.substring(0, 10)))
        
      } yield {
        assert(orderDates.forall(date => 
          !date.isBefore(startDate) && !date.isAfter(endDate)
        ))(isTrue)
      }
    }.provide(
      SeamlessSchwabClient.live,
      SchwabOrderServiceEnhanced.layer
    ) @@ TestAspect.timeout(60.seconds),
    
    test("analyze PM trading metrics") {
      for {
        today <- ZIO.succeed(LocalDate.now())
        sixMonthsAgo <- ZIO.succeed(today.minusMonths(6))
        
        orders <- SchwabOrderServiceEnhanced.getOrdersForSymbol(
          symbol = "PM",
          fromDate = sixMonthsAgo,
          toDate = today
        )
        
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
        
        _ <- TestConsole.printLine(f"PM Trading Summary (6 months):")
        _ <- TestConsole.printLine(f"  Total bought: ${totalBuyQuantity}%.0f shares")
        _ <- TestConsole.printLine(f"  Total sold: ${totalSellQuantity}%.0f shares")
        _ <- TestConsole.printLine(f"  Net position change: ${netPosition}%.0f shares")
        
      } yield {
        // Verify calculations are non-negative
        assert(totalBuyQuantity)(isGreaterThanEqualTo(0.0)) &&
        assert(totalSellQuantity)(isGreaterThanEqualTo(0.0))
      }
    }.provide(
      SeamlessSchwabClient.live,
      SchwabOrderServiceEnhanced.layer
    ) @@ TestAspect.timeout(60.seconds)
  )
}