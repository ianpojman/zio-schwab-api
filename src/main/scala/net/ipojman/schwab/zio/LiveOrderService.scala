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
  
  // Trading endpoints take a token from the OAuth authorization code flow; a client credentials token is refused.
  private def tradingToken: Task[TokenResponse] = client match {
    case seamless: SeamlessSchwabClient => seamless.ensureAuthenticated()
    case _ => ZIO.fail(new RuntimeException("Trading endpoints require SeamlessSchwabClient with OAuth authorization"))
  }

  // The three write calls below are each sent once. None of them retries, on any error.

  override def placeOrder(
    accountNumber: String,
    order: OrderRequest
  ): Task[Long] = {
    for {
      token    <- tradingToken
      response <- client.makeWriteApiCall(
                    Method.POST,
                    s"$baseEndpoint/accounts/$accountNumber/orders",
                    token.access_token,
                    Some(order.toJson)
                  )
      orderId  <- LiveOrderService.orderIdFrom(response).someOrFail(
                    new OrderIdNotReturned(response.status, response.location, response.body)
                  )
    } yield orderId
  }

  override def replaceOrder(
    accountNumber: String,
    orderId: Long,
    order: OrderRequest
  ): Task[Option[Long]] = {
    for {
      token    <- tradingToken
      response <- client.makeWriteApiCall(
                    Method.PUT,
                    s"$baseEndpoint/accounts/$accountNumber/orders/$orderId",
                    token.access_token,
                    Some(order.toJson)
                  )
      newId    <- LiveOrderService.orderIdFrom(response)
    } yield newId
  }

  override def cancelOrder(
    accountNumber: String,
    orderId: Long
  ): Task[Unit] = {
    for {
      token <- tradingToken
      _     <- client.makeWriteApiCall(
                 Method.DELETE,
                 s"$baseEndpoint/accounts/$accountNumber/orders/$orderId",
                 token.access_token,
                 None
               )
    } yield ()
  }
}

object LiveOrderService {
  val layer: ZLayer[SchwabClient, Nothing, OrderService] =
    ZLayer.fromFunction(LiveOrderService(_))

  /**
   * The order id named by a write reply's Location header, which ends in /orders/{orderId}.
   * None when there is no Location header. Fails with [[OrderIdNotReturned]] when the header
   * is there but its last path segment is not a number.
   */
  def orderIdFrom(response: ApiWriteResponse): Task[Option[Long]] =
    response.location match {
      case None => ZIO.none
      case Some(location) =>
        val lastSegment = location.takeWhile(c => c != '?' && c != '#').split('/').filter(_.nonEmpty).lastOption
        lastSegment.flatMap(_.toLongOption) match {
          case Some(id) => ZIO.some(id)
          case None     => ZIO.fail(new OrderIdNotReturned(response.status, response.location, response.body))
        }
    }
}