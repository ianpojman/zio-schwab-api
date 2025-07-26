package net.ipojman.schwab.zio

import zio.*
import zio.http.*
import zio.json.*
import net.ipojman.schwab.zio.models.*
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Live implementation of OrderService that uses the SchwabClient
 */
case class LiveOrderService(client: SchwabClient) extends OrderService {
  
  private val baseEndpoint = "trader/v1"
  private val dateFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
  
  private def buildQueryParams(params: (String, Option[Any])*): String = {
    val validParams = params.collect {
      case (key, Some(value)) => s"$key=$value"
    }
    if (validParams.isEmpty) "" else "?" + validParams.mkString("&")
  }
  
  override def getOrders(
    fromEnteredTime: ZonedDateTime,
    toEnteredTime: ZonedDateTime,
    maxResults: Option[Int] = None,
    status: Option[OrderStatus] = None
  ): Task[List[Order]] = {
    val queryParams = buildQueryParams(
      "fromEnteredTime" -> Some(fromEnteredTime.format(dateFormatter)),
      "toEnteredTime" -> Some(toEnteredTime.format(dateFormatter)),
      "maxResults" -> maxResults,
      "status" -> status.map(OrderStatus.encoder.encodeJson(_).toString.stripPrefix("\"").stripSuffix("\""))
    )
    
    for {
      // Trading endpoints require OAuth authorization code flow, not client credentials
      token <- client match {
        case seamless: SeamlessSchwabClient => seamless.ensureAuthenticated()
        case _ => ZIO.fail(new RuntimeException("Trading endpoints require SeamlessSchwabClient with OAuth authorization"))
      }
      response <- client.makeApiCall[List[Order]](
        s"$baseEndpoint/orders$queryParams",
        token.access_token
      )
    } yield response
  }
  
  override def getOrdersByAccount(
    accountNumber: String,
    fromEnteredTime: ZonedDateTime,
    toEnteredTime: ZonedDateTime,
    maxResults: Option[Int] = None,
    status: Option[OrderStatus] = None
  ): Task[List[Order]] = {
    val queryParams = buildQueryParams(
      "fromEnteredTime" -> Some(fromEnteredTime.format(dateFormatter)),
      "toEnteredTime" -> Some(toEnteredTime.format(dateFormatter)),
      "maxResults" -> maxResults,
      "status" -> status.map(OrderStatus.encoder.encodeJson(_).toString.stripPrefix("\"").stripSuffix("\""))
    )
    
    for {
      token <- client match {
        case seamless: SeamlessSchwabClient => seamless.ensureAuthenticated()
        case _ => ZIO.fail(new RuntimeException("Trading endpoints require SeamlessSchwabClient with OAuth authorization"))
      }
      response <- client.makeApiCall[List[Order]](
        s"$baseEndpoint/accounts/$accountNumber/orders$queryParams",
        token.access_token
      )
    } yield response
  }
  
  override def getOrder(
    accountNumber: String,
    orderId: Long
  ): Task[Order] = {
    for {
      token <- client match {
        case seamless: SeamlessSchwabClient => seamless.ensureAuthenticated()
        case _ => ZIO.fail(new RuntimeException("Trading endpoints require SeamlessSchwabClient with OAuth authorization"))
      }
      response <- client.makeApiCall[Order](
        s"$baseEndpoint/accounts/$accountNumber/orders/$orderId",
        token.access_token
      )
    } yield response
  }
  
  override def placeOrder(
    accountNumber: String,
    order: OrderRequest
  ): Task[Long] = {
    for {
      token <- client match {
        case seamless: SeamlessSchwabClient => seamless.ensureAuthenticated()
        case _ => ZIO.fail(new RuntimeException("Trading endpoints require SeamlessSchwabClient with OAuth authorization"))
      }
      // For POST requests, we need to implement a new method in SchwabClient
      // For now, we'll return a placeholder
      _ <- ZIO.fail(new NotImplementedError("POST requests not yet implemented in SchwabClient"))
    } yield 0L
  }
  
  override def replaceOrder(
    accountNumber: String,
    orderId: Long,
    order: OrderRequest
  ): Task[Long] = {
    for {
      token <- client match {
        case seamless: SeamlessSchwabClient => seamless.ensureAuthenticated()
        case _ => ZIO.fail(new RuntimeException("Trading endpoints require SeamlessSchwabClient with OAuth authorization"))
      }
      // For PUT requests, we need to implement a new method in SchwabClient
      // For now, we'll return a placeholder
      _ <- ZIO.fail(new NotImplementedError("PUT requests not yet implemented in SchwabClient"))
    } yield 0L
  }
  
  override def cancelOrder(
    accountNumber: String,
    orderId: Long
  ): Task[Unit] = {
    for {
      token <- client match {
        case seamless: SeamlessSchwabClient => seamless.ensureAuthenticated()
        case _ => ZIO.fail(new RuntimeException("Trading endpoints require SeamlessSchwabClient with OAuth authorization"))
      }
      // For DELETE requests, we need to implement a new method in SchwabClient
      // For now, we'll return a placeholder
      _ <- ZIO.fail(new NotImplementedError("DELETE requests not yet implemented in SchwabClient"))
    } yield ()
  }
}

object LiveOrderService {
  val layer: ZLayer[SchwabClient, Nothing, OrderService] =
    ZLayer.fromFunction(LiveOrderService(_))
}