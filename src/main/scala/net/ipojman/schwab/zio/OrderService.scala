package net.ipojman.schwab.zio

import zio.*
import net.ipojman.schwab.zio.models.*
import java.time.ZonedDateTime

/**
 * Service interface for Schwab Trading API - Orders
 * 
 * Base URL: https://api.schwabapi.com/trader/v1
 */
trait OrderService {
  
  /**
   * Get all orders for all accounts within a date range
   * GET /orders
   * 
   * @param fromEnteredTime Start date/time for orders (must be within 60 days from today)
   * @param toEnteredTime End date/time for orders
   * @param maxResults Maximum number of orders to retrieve (default: 3000)
   * @param status Filter by order status (e.g., FILLED, WORKING, CANCELED)
   * @return List of orders across all accounts
   */
  def getOrders(
    fromEnteredTime: ZonedDateTime,
    toEnteredTime: ZonedDateTime,
    maxResults: Option[Int] = None,
    status: Option[OrderStatus] = None
  ): Task[List[Order]]
  
  /**
   * Get orders for a specific account within a date range
   * GET /accounts/{accountNumber}/orders
   * 
   * @param accountNumber The account number
   * @param fromEnteredTime Start date/time for orders (must be within 60 days from today)
   * @param toEnteredTime End date/time for orders
   * @param maxResults Maximum number of orders to retrieve
   * @param status Filter by order status
   * @return List of orders for the specified account
   */
  def getOrdersByAccount(
    accountNumber: String,
    fromEnteredTime: ZonedDateTime,
    toEnteredTime: ZonedDateTime,
    maxResults: Option[Int] = None,
    status: Option[OrderStatus] = None
  ): Task[List[Order]]
  
  /**
   * Get a specific order by order ID
   * GET /accounts/{accountNumber}/orders/{orderId}
   * 
   * @param accountNumber The account number
   * @param orderId The order ID
   * @return The specific order
   */
  def getOrder(
    accountNumber: String,
    orderId: Long
  ): Task[Order]
  
  /**
   * Place a new order for a specific account
   * POST /accounts/{accountNumber}/orders
   * 
   * Sent once, never retried. The new order id is read from the reply's Location header.
   * A 2xx reply without a readable id fails with [[OrderIdNotReturned]]: the order may be
   * live, so the caller should find it among the account's open orders and not send it again.
   * 
   * @param accountNumber The account hash (from /accounts/accountNumbers), as for the other order calls
   * @param order The order to place
   * @return The order ID of the placed order
   */
  def placeOrder(
    accountNumber: String,
    order: OrderRequest
  ): Task[Long]
  
  /**
   * Replace an existing order
   * PUT /accounts/{accountNumber}/orders/{orderId}
   * 
   * A replace cancels the old order and creates a new one with a new order id. Sent once,
   * never retried.
   * 
   * @param accountNumber The account hash, as for the other order calls
   * @param orderId The order ID to replace
   * @param order The replacement order
   * @return Some(new order id) when the reply's Location header names it; None when the
   *         reply carries no Location header, in which case the caller finds the new order
   *         among the account's open orders
   */
  def replaceOrder(
    accountNumber: String,
    orderId: Long,
    order: OrderRequest
  ): Task[Option[Long]]
  
  /**
   * Cancel a specific order
   * DELETE /accounts/{accountNumber}/orders/{orderId}
   * 
   * Sent once, never retried.
   * 
   * @param accountNumber The account hash, as for the other order calls
   * @param orderId The order ID to cancel
   * @return Unit on successful cancellation
   */
  def cancelOrder(
    accountNumber: String,
    orderId: Long
  ): Task[Unit]
}

object OrderService {
  def getOrders(
    fromEnteredTime: ZonedDateTime,
    toEnteredTime: ZonedDateTime,
    maxResults: Option[Int] = None,
    status: Option[OrderStatus] = None
  ): ZIO[OrderService, Throwable, List[Order]] =
    ZIO.serviceWithZIO[OrderService](_.getOrders(fromEnteredTime, toEnteredTime, maxResults, status))
    
  def getOrdersByAccount(
    accountNumber: String,
    fromEnteredTime: ZonedDateTime,
    toEnteredTime: ZonedDateTime,
    maxResults: Option[Int] = None,
    status: Option[OrderStatus] = None
  ): ZIO[OrderService, Throwable, List[Order]] =
    ZIO.serviceWithZIO[OrderService](_.getOrdersByAccount(accountNumber, fromEnteredTime, toEnteredTime, maxResults, status))
    
  def getOrder(
    accountNumber: String,
    orderId: Long
  ): ZIO[OrderService, Throwable, Order] =
    ZIO.serviceWithZIO[OrderService](_.getOrder(accountNumber, orderId))
    
  def placeOrder(
    accountNumber: String,
    order: OrderRequest
  ): ZIO[OrderService, Throwable, Long] =
    ZIO.serviceWithZIO[OrderService](_.placeOrder(accountNumber, order))
    
  def replaceOrder(
    accountNumber: String,
    orderId: Long,
    order: OrderRequest
  ): ZIO[OrderService, Throwable, Option[Long]] =
    ZIO.serviceWithZIO[OrderService](_.replaceOrder(accountNumber, orderId, order))
    
  def cancelOrder(
    accountNumber: String,
    orderId: Long
  ): ZIO[OrderService, Throwable, Unit] =
    ZIO.serviceWithZIO[OrderService](_.cancelOrder(accountNumber, orderId))
}

/**
 * The broker answered a place or replace with a 2xx status but no order id could be read
 * from its Location header. The order may be live: find it among the account's open orders
 * rather than sending it again.
 */
class OrderIdNotReturned(val status: Int, val location: Option[String], val body: String)
  extends RuntimeException(
    s"Order accepted with status $status but no order id could be read from Location: ${location.getOrElse("(none)")}. " +
      "The order may be live; find it among the account's open orders and do not send it again."
  )
