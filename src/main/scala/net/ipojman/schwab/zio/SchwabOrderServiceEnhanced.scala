package net.ipojman.schwab.zio

import com.typesafe.scalalogging.LazyLogging
import net.ipojman.schwab.zio.models.*
import zio.*
import zio.json.*
import java.time.{ZonedDateTime, LocalDate, ZoneOffset}
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter

case class SimplifiedOrder(
  orderId: Long,
  symbol: String,
  quantity: Double,
  price: Option[Double],
  orderType: String,
  instruction: String,  // BUY or SELL
  status: OrderStatus,
  enteredTime: ZonedDateTime,
  filledTime: Option[ZonedDateTime],
  accountNumber: Long
)

trait SchwabOrderServiceEnhanced {
  def getOrdersForSymbol(symbol: String, fromDate: LocalDate, toDate: LocalDate): Task[List[SimplifiedOrder]]
  def getFilledOrdersForSymbol(symbol: String, fromDate: LocalDate, toDate: LocalDate): Task[List[SimplifiedOrder]]
  def getOrdersForAllSymbols(fromDate: LocalDate, toDate: LocalDate): Task[List[SimplifiedOrder]]
}

class SchwabOrderServiceEnhancedImpl(
  schwabClient: SchwabClient
) extends SchwabOrderServiceEnhanced with LazyLogging {
  
  private def ensureValidToken(): Task[TokenResponse] = {
    // The seamless client handles token management automatically
    schwabClient match {
      case seamless: SeamlessSchwabClient =>
        seamless.ensureAuthenticated()
      case _ =>
        ZIO.fail(new Exception("SchwabOrderServiceEnhanced requires SeamlessSchwabClient"))
    }
  }
  
  // Fixed Order model that handles childOrderStrategies properly
  case class OrderWithFixedChildStrategies(
    session: String,
    duration: String,
    orderType: String,
    cancelTime: Option[ZonedDateTime] = None,
    complexOrderStrategyType: String,
    quantity: Double,
    filledQuantity: Double,
    remainingQuantity: Double,
    requestedDestination: String,
    destinationLinkName: Option[String] = None,
    releaseTime: Option[ZonedDateTime] = None,
    stopPrice: Option[Double] = None,
    stopPriceLinkBasis: Option[String] = None,
    stopPriceLinkType: Option[String] = None,
    stopPriceOffset: Option[Double] = None,
    stopType: Option[String] = None,
    priceLinkBasis: Option[String] = None,
    priceLinkType: Option[String] = None,
    price: Option[Double] = None,
    taxLotMethod: Option[String] = None,
    orderLegCollection: List[OrderLeg],
    activationPrice: Option[Double] = None,
    specialInstruction: Option[String] = None,
    orderStrategyType: String,
    orderId: Long,
    cancelable: Boolean,
    editable: Boolean,
    status: OrderStatus,
    enteredTime: ZonedDateTime,
    closeTime: Option[ZonedDateTime] = None,
    tag: Option[String] = None,
    accountNumber: Long,
    orderActivityCollection: Option[List[OrderActivity]] = None,
    replacingOrderCollection: Option[List[String]] = None,
    childOrderStrategies: Option[List[String]] = None,  // This should be List[String] per API docs
    statusDescription: Option[String] = None
  )
  
  object OrderWithFixedChildStrategies {
    implicit val zonedDateTimeDecoder: JsonDecoder[ZonedDateTime] = Order.zonedDateTimeDecoder
    implicit val zonedDateTimeEncoder: JsonEncoder[ZonedDateTime] = Order.zonedDateTimeEncoder
    
    implicit val decoder: JsonDecoder[OrderWithFixedChildStrategies] = DeriveJsonDecoder.gen[OrderWithFixedChildStrategies]
    implicit val encoder: JsonEncoder[OrderWithFixedChildStrategies] = DeriveJsonEncoder.gen[OrderWithFixedChildStrategies]
  }
  
  override def getOrdersForSymbol(symbol: String, fromDate: LocalDate, toDate: LocalDate): Task[List[SimplifiedOrder]] = {
    val makeRequest = (token: TokenResponse) => {
      for {
        _ <- ZIO.logInfo(s"Loading orders for symbol: $symbol from $fromDate to $toDate")
        
        // Convert dates to ZonedDateTime at start/end of day in UTC
        // Schwab expects ISO-8601 format: yyyy-MM-dd'T'HH:mm:ss.SSSZ
        fromTime = fromDate.atStartOfDay(ZoneOffset.UTC)
        toTime = toDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).minusSeconds(1)
        
        // Format timestamps according to Schwab API requirements
        formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        fromTimeStr = fromTime.format(formatter)
        toTimeStr = toTime.format(formatter)
        
        _ <- ZIO.logInfo(s"Formatted timestamps: from=$fromTimeStr, to=$toTimeStr")
        
        // Get orders from API - omit status parameter to get all orders
        ordersJson <- schwabClient.makeRawApiCall(
          s"trader/v1/orders?fromEnteredTime=$fromTimeStr&toEnteredTime=$toTimeStr&maxResults=500",
          token.access_token
        ).mapError(e => new Exception(s"Failed to load orders: ${e.getMessage}", e))
      } yield ordersJson
    }
    
    for {
      token <- ensureValidToken()
      ordersJson <- makeRequest(token).catchSome {
        case e: Exception if e.getMessage.contains("401") =>
          ZIO.logInfo("Received 401, attempting to refresh token...") *>
          ensureValidToken().flatMap(makeRequest)
      }
      
      // Pre-process JSON to handle childOrderStrategies field variations
      processedJson = {
        import zio.json.ast._
        ordersJson.fromJson[Json] match {
          case Right(Json.Arr(orders)) =>
            val processedOrders = orders.map {
              case Json.Obj(fields) =>
                val updatedFields = fields.map {
                  case ("childOrderStrategies", Json.Arr(children)) =>
                    // Convert any non-string elements to empty array
                    val processedChildren = children.flatMap {
                      case Json.Str(s) => Some(Json.Str(s))
                      case _ => None // Skip non-string elements
                    }
                    ("childOrderStrategies", if (processedChildren.isEmpty) Json.Null else Json.Arr(processedChildren))
                  case other => other
                }
                Json.Obj(updatedFields)
              case other => other
            }
            Json.Arr(processedOrders).toJson
          case _ => ordersJson
        }
      }
      
      // Parse orders using the fixed model
      orders <- ZIO.fromEither(processedJson.fromJson[List[OrderWithFixedChildStrategies]])
        .mapError(e => new Exception(s"Failed to parse orders: $e"))
        .catchAll { err =>
          // If parsing fails, log the raw JSON for debugging
          ZIO.logError(s"Failed to parse orders JSON. Error: ${err.getMessage}") *>
          ZIO.logDebug(s"Raw JSON response (first 1000 chars): ${ordersJson.take(1000)}") *>
          // Try parsing with the original Order model as fallback
          ZIO.fromEither(ordersJson.fromJson[List[Order]])
            .map { standardOrders =>
              // Convert to our fixed model
              standardOrders.map { order =>
                OrderWithFixedChildStrategies(
                  session = order.session,
                  duration = order.duration,
                  orderType = order.orderType,
                  cancelTime = order.cancelTime,
                  complexOrderStrategyType = order.complexOrderStrategyType,
                  quantity = order.quantity,
                  filledQuantity = order.filledQuantity,
                  remainingQuantity = order.remainingQuantity,
                  requestedDestination = order.requestedDestination,
                  destinationLinkName = order.destinationLinkName,
                  releaseTime = order.releaseTime,
                  stopPrice = order.stopPrice,
                  stopPriceLinkBasis = order.stopPriceLinkBasis,
                  stopPriceLinkType = order.stopPriceLinkType,
                  stopPriceOffset = order.stopPriceOffset,
                  stopType = order.stopType,
                  priceLinkBasis = order.priceLinkBasis,
                  priceLinkType = order.priceLinkType,
                  price = order.price,
                  taxLotMethod = order.taxLotMethod,
                  orderLegCollection = order.orderLegCollection,
                  activationPrice = order.activationPrice,
                  specialInstruction = order.specialInstruction,
                  orderStrategyType = order.orderStrategyType,
                  orderId = order.orderId,
                  cancelable = order.cancelable,
                  editable = order.editable,
                  status = order.status,
                  enteredTime = order.enteredTime,
                  closeTime = order.closeTime,
                  tag = order.tag,
                  accountNumber = order.accountNumber,
                  orderActivityCollection = order.orderActivityCollection,
                  replacingOrderCollection = order.replacingOrderCollection,
                  childOrderStrategies = None,  // Ignore due to parsing issues
                  statusDescription = order.statusDescription
                )
              }
            }
            .mapError(e2 => new Exception(s"Failed to parse orders with both models. Original error: ${err.getMessage}, Fallback error: $e2"))
        }
      
      // Filter and convert to simplified format
      symbolOrders = orders.flatMap { order =>
        // Extract orders that contain the requested symbol
        val matchingLegs = order.orderLegCollection.filter(_.instrument.symbol.equalsIgnoreCase(symbol))
        
        matchingLegs.map { leg =>
          SimplifiedOrder(
            orderId = order.orderId,
            symbol = leg.instrument.symbol,
            quantity = leg.quantity,
            price = order.price.orElse {
              // Try to get filled price from activity collection
              order.orderActivityCollection.flatMap(_.headOption).flatMap { activity =>
                activity.executionLegs.flatMap(_.headOption).map(_.price)
              }
            },
            orderType = order.orderType,
            instruction = leg.instruction,
            status = order.status,
            enteredTime = order.enteredTime,
            filledTime = if (order.status == OrderStatus.Filled) order.closeTime else None,
            accountNumber = order.accountNumber
          )
        }
      }
      
      _ <- ZIO.logInfo(s"Found ${symbolOrders.size} orders for $symbol")
      
    } yield symbolOrders
  }
  
  override def getFilledOrdersForSymbol(symbol: String, fromDate: LocalDate, toDate: LocalDate): Task[List[SimplifiedOrder]] = {
    getOrdersForSymbol(symbol, fromDate, toDate).map(_.filter(_.status == OrderStatus.Filled))
  }
  
  override def getOrdersForAllSymbols(fromDate: LocalDate, toDate: LocalDate): Task[List[SimplifiedOrder]] = {
    for {
      _ <- ZIO.logInfo(s"Loading all orders from $fromDate to $toDate")
      token <- ensureValidToken()
      
      // Convert dates to ZonedDateTime at start/end of day in UTC
      fromTime = fromDate.atStartOfDay(ZoneOffset.UTC)
      toTime = toDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).minusSeconds(1)
      
      // Format timestamps according to Schwab API requirements
      formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
      fromTimeStr = fromTime.format(formatter)
      toTimeStr = toTime.format(formatter)
      
      _ <- ZIO.logInfo(s"Formatted timestamps: from=$fromTimeStr, to=$toTimeStr")
      
      // Get orders from API - omit status parameter to get all orders
      ordersJson <- schwabClient.makeRawApiCall(
        s"trader/v1/orders?fromEnteredTime=$fromTimeStr&toEnteredTime=$toTimeStr&maxResults=500",
        token.access_token
      ).mapError(e => new Exception(s"Failed to load orders: ${e.getMessage}", e))
      
      // Pre-process JSON to handle childOrderStrategies field variations
      processedJson = {
        import zio.json.ast._
        ordersJson.fromJson[Json] match {
          case Right(Json.Arr(orders)) =>
            val processedOrders = orders.map {
              case Json.Obj(fields) =>
                val updatedFields = fields.map {
                  case ("childOrderStrategies", Json.Arr(children)) =>
                    // Convert any non-string elements to empty array
                    val processedChildren = children.flatMap {
                      case Json.Str(s) => Some(Json.Str(s))
                      case _ => None // Skip non-string elements
                    }
                    ("childOrderStrategies", if (processedChildren.isEmpty) Json.Null else Json.Arr(processedChildren))
                  case other => other
                }
                Json.Obj(updatedFields)
              case other => other
            }
            Json.Arr(processedOrders).toJson
          case _ => ordersJson
        }
      }
      
      // Parse orders using the fixed model
      orders <- ZIO.fromEither(processedJson.fromJson[List[OrderWithFixedChildStrategies]])
        .mapError(e => new Exception(s"Failed to parse orders: $e"))
        .catchAll { err =>
          // If parsing fails, log the raw JSON for debugging
          ZIO.logError(s"Failed to parse orders JSON. Error: ${err.getMessage}") *>
          ZIO.logDebug(s"Raw JSON response (first 1000 chars): ${ordersJson.take(1000)}") *>
          // Try parsing with the original Order model as fallback
          ZIO.fromEither(ordersJson.fromJson[List[Order]])
            .map { standardOrders =>
              // Convert to our fixed model
              standardOrders.map { order =>
                OrderWithFixedChildStrategies(
                  session = order.session,
                  duration = order.duration,
                  orderType = order.orderType,
                  cancelTime = order.cancelTime,
                  complexOrderStrategyType = order.complexOrderStrategyType,
                  quantity = order.quantity,
                  filledQuantity = order.filledQuantity,
                  remainingQuantity = order.remainingQuantity,
                  requestedDestination = order.requestedDestination,
                  destinationLinkName = order.destinationLinkName,
                  releaseTime = order.releaseTime,
                  stopPrice = order.stopPrice,
                  stopPriceLinkBasis = order.stopPriceLinkBasis,
                  stopPriceLinkType = order.stopPriceLinkType,
                  stopPriceOffset = order.stopPriceOffset,
                  stopType = order.stopType,
                  priceLinkBasis = order.priceLinkBasis,
                  priceLinkType = order.priceLinkType,
                  price = order.price,
                  taxLotMethod = order.taxLotMethod,
                  orderLegCollection = order.orderLegCollection,
                  activationPrice = order.activationPrice,
                  specialInstruction = order.specialInstruction,
                  orderStrategyType = order.orderStrategyType,
                  orderId = order.orderId,
                  cancelable = order.cancelable,
                  editable = order.editable,
                  status = order.status,
                  enteredTime = order.enteredTime,
                  closeTime = order.closeTime,
                  tag = order.tag,
                  accountNumber = order.accountNumber,
                  orderActivityCollection = order.orderActivityCollection,
                  replacingOrderCollection = order.replacingOrderCollection,
                  childOrderStrategies = None,  // Ignore due to parsing issues
                  statusDescription = order.statusDescription
                )
              }
            }
            .mapError(e2 => new Exception(s"Failed to parse orders with both models. Original error: ${err.getMessage}, Fallback error: $e2"))
        }
      
      // Convert all orders to simplified format
      simplifiedOrders = orders.flatMap { order =>
        order.orderLegCollection.map { leg =>
          SimplifiedOrder(
            orderId = order.orderId,
            symbol = leg.instrument.symbol,
            quantity = leg.quantity,
            price = order.price.orElse {
              // Try to get filled price from activity collection
              order.orderActivityCollection.flatMap(_.headOption).flatMap { activity =>
                activity.executionLegs.flatMap(_.headOption).map(_.price)
              }
            },
            orderType = order.orderType,
            instruction = leg.instruction,
            status = order.status,
            enteredTime = order.enteredTime,
            filledTime = if (order.status == OrderStatus.Filled) order.closeTime else None,
            accountNumber = order.accountNumber
          )
        }
      }
      
      _ <- ZIO.logInfo(s"Found ${simplifiedOrders.size} total orders")
      
    } yield simplifiedOrders
  }
}

object SchwabOrderServiceEnhanced {
  val layer: ZLayer[SchwabClient, Nothing, SchwabOrderServiceEnhanced] = 
    ZLayer {
      for {
        client <- ZIO.service[SchwabClient]
      } yield new SchwabOrderServiceEnhancedImpl(client)
    }
}