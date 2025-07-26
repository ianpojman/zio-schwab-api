package net.ipojman.schwab.zio.models

import zio.json.*
import java.time.{ZonedDateTime, OffsetDateTime}
import java.time.format.DateTimeFormatter

// Order status enum
sealed trait OrderStatus
object OrderStatus {
  case object AwaitingParentOrder extends OrderStatus
  case object AwaitingCondition extends OrderStatus
  case object AwaitingStopCondition extends OrderStatus
  case object AwaitingManualReview extends OrderStatus
  case object Accepted extends OrderStatus
  case object AwaitingUrOut extends OrderStatus
  case object PendingActivation extends OrderStatus
  case object Queued extends OrderStatus
  case object Working extends OrderStatus
  case object Rejected extends OrderStatus
  case object PendingCancel extends OrderStatus
  case object Canceled extends OrderStatus
  case object PendingReplace extends OrderStatus
  case object Replaced extends OrderStatus
  case object Filled extends OrderStatus
  case object Expired extends OrderStatus
  case object New extends OrderStatus
  case object AwaitingReleaseTime extends OrderStatus
  case object PendingAcknowledgement extends OrderStatus
  case object PendingRecall extends OrderStatus
  case object Unknown extends OrderStatus
  
  implicit val decoder: JsonDecoder[OrderStatus] = JsonDecoder[String].map {
    case "AWAITING_PARENT_ORDER" => AwaitingParentOrder
    case "AWAITING_CONDITION" => AwaitingCondition
    case "AWAITING_STOP_CONDITION" => AwaitingStopCondition
    case "AWAITING_MANUAL_REVIEW" => AwaitingManualReview
    case "ACCEPTED" => Accepted
    case "AWAITING_UR_OUT" => AwaitingUrOut
    case "PENDING_ACTIVATION" => PendingActivation
    case "QUEUED" => Queued
    case "WORKING" => Working
    case "REJECTED" => Rejected
    case "PENDING_CANCEL" => PendingCancel
    case "CANCELED" => Canceled
    case "PENDING_REPLACE" => PendingReplace
    case "REPLACED" => Replaced
    case "FILLED" => Filled
    case "EXPIRED" => Expired
    case "NEW" => New
    case "AWAITING_RELEASE_TIME" => AwaitingReleaseTime
    case "PENDING_ACKNOWLEDGEMENT" => PendingAcknowledgement
    case "PENDING_RECALL" => PendingRecall
    case _ => Unknown
  }
  
  implicit val encoder: JsonEncoder[OrderStatus] = JsonEncoder[String].contramap {
    case AwaitingParentOrder => "AWAITING_PARENT_ORDER"
    case AwaitingCondition => "AWAITING_CONDITION"
    case AwaitingStopCondition => "AWAITING_STOP_CONDITION"
    case AwaitingManualReview => "AWAITING_MANUAL_REVIEW"
    case Accepted => "ACCEPTED"
    case AwaitingUrOut => "AWAITING_UR_OUT"
    case PendingActivation => "PENDING_ACTIVATION"
    case Queued => "QUEUED"
    case Working => "WORKING"
    case Rejected => "REJECTED"
    case PendingCancel => "PENDING_CANCEL"
    case Canceled => "CANCELED"
    case PendingReplace => "PENDING_REPLACE"
    case Replaced => "REPLACED"
    case Filled => "FILLED"
    case Expired => "EXPIRED"
    case New => "NEW"
    case AwaitingReleaseTime => "AWAITING_RELEASE_TIME"
    case PendingAcknowledgement => "PENDING_ACKNOWLEDGEMENT"
    case PendingRecall => "PENDING_RECALL"
    case Unknown => "UNKNOWN"
  }
}

// Order model based on the API response
case class Order(
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
  childOrderStrategies: Option[zio.json.ast.Json] = None,
  statusDescription: Option[String] = None
)

object Order {
  // Custom decoder for Schwab's date format "2025-07-25T19:57:06+0000"
  implicit val zonedDateTimeDecoder: JsonDecoder[ZonedDateTime] = JsonDecoder[String].mapOrFail { str =>
    try {
      // Try standard ISO format first
      Right(ZonedDateTime.parse(str))
    } catch {
      case _: Exception =>
        try {
          // Try Schwab's format: "2025-07-25T19:57:06+0000"
          val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")
          Right(ZonedDateTime.parse(str, formatter))
        } catch {
          case _: Exception =>
            try {
              // Try with OffsetDateTime
              Right(OffsetDateTime.parse(str).toZonedDateTime)
            } catch {
              case e: Exception => Left(s"Cannot parse date: $str")
            }
        }
    }
  }
  
  implicit val zonedDateTimeEncoder: JsonEncoder[ZonedDateTime] = JsonEncoder[String].contramap(_.toString)
  
  implicit val decoder: JsonDecoder[Order] = DeriveJsonDecoder.gen[Order]
  implicit val encoder: JsonEncoder[Order] = DeriveJsonEncoder.gen[Order]
}

case class OrderLeg(
  orderLegType: String,
  legId: Long,
  instrument: OrderInstrument,
  instruction: String,
  positionEffect: Option[String] = None,
  quantity: Double,
  quantityType: Option[String] = None,
  divCapGains: Option[String] = None,
  toSymbol: Option[String] = None
)

object OrderLeg {
  implicit val decoder: JsonDecoder[OrderLeg] = DeriveJsonDecoder.gen[OrderLeg]
  implicit val encoder: JsonEncoder[OrderLeg] = DeriveJsonEncoder.gen[OrderLeg]
}

case class OrderInstrument(
  cusip: Option[String] = None,
  symbol: String,
  description: Option[String] = None,
  instrumentId: Option[Long] = None,
  netChange: Option[Double] = None,
  `type`: Option[String] = None,  // Made optional since it may not always be present
  assetType: Option[String] = None  // API also returns assetType
)

object OrderInstrument {
  implicit val decoder: JsonDecoder[OrderInstrument] = DeriveJsonDecoder.gen[OrderInstrument]
  implicit val encoder: JsonEncoder[OrderInstrument] = DeriveJsonEncoder.gen[OrderInstrument]
}

case class OrderActivity(
  activityType: String,
  executionType: Option[String] = None,
  quantity: Double,
  orderRemainingQuantity: Double,
  executionLegs: Option[List[ExecutionLeg]] = None
)

object OrderActivity {
  implicit val decoder: JsonDecoder[OrderActivity] = DeriveJsonDecoder.gen[OrderActivity]
  implicit val encoder: JsonEncoder[OrderActivity] = DeriveJsonEncoder.gen[OrderActivity]
}

case class ExecutionLeg(
  legId: Long,
  price: Double,
  quantity: Double,
  mismarkedQuantity: Double,
  instrumentId: Long,
  time: ZonedDateTime
)

object ExecutionLeg {
  implicit val zonedDateTimeDecoder: JsonDecoder[ZonedDateTime] = Order.zonedDateTimeDecoder
  implicit val zonedDateTimeEncoder: JsonEncoder[ZonedDateTime] = Order.zonedDateTimeEncoder
  
  implicit val decoder: JsonDecoder[ExecutionLeg] = DeriveJsonDecoder.gen[ExecutionLeg]
  implicit val encoder: JsonEncoder[ExecutionLeg] = DeriveJsonEncoder.gen[ExecutionLeg]
}

// For placing orders
case class OrderRequest(
  session: String,
  duration: String,
  orderType: String,
  complexOrderStrategyType: String = "NONE",
  quantity: Double,
  requestedDestination: Option[String] = None,
  stopPrice: Option[Double] = None,
  stopPriceLinkBasis: Option[String] = None,
  stopPriceLinkType: Option[String] = None,
  stopPriceOffset: Option[Double] = None,
  stopType: Option[String] = None,
  priceLinkBasis: Option[String] = None,
  priceLinkType: Option[String] = None,
  price: Option[Double] = None,
  taxLotMethod: Option[String] = None,
  orderLegCollection: List[OrderLegRequest],
  activationPrice: Option[Double] = None,
  specialInstruction: Option[String] = None,
  orderStrategyType: String = "SINGLE",
  releaseTime: Option[ZonedDateTime] = None,
  cancelTime: Option[ZonedDateTime] = None
)

object OrderRequest {
  implicit val zonedDateTimeDecoder: JsonDecoder[ZonedDateTime] = Order.zonedDateTimeDecoder
  implicit val zonedDateTimeEncoder: JsonEncoder[ZonedDateTime] = Order.zonedDateTimeEncoder
  
  implicit val decoder: JsonDecoder[OrderRequest] = DeriveJsonDecoder.gen[OrderRequest]
  implicit val encoder: JsonEncoder[OrderRequest] = DeriveJsonEncoder.gen[OrderRequest]
}

case class OrderLegRequest(
  orderLegType: String,
  instrument: OrderInstrumentRequest,
  instruction: String,
  positionEffect: Option[String] = None,
  quantity: Double,
  quantityType: Option[String] = None
)

object OrderLegRequest {
  implicit val decoder: JsonDecoder[OrderLegRequest] = DeriveJsonDecoder.gen[OrderLegRequest]
  implicit val encoder: JsonEncoder[OrderLegRequest] = DeriveJsonEncoder.gen[OrderLegRequest]
}

case class OrderInstrumentRequest(
  symbol: String,
  assetType: String
)

object OrderInstrumentRequest {
  implicit val decoder: JsonDecoder[OrderInstrumentRequest] = DeriveJsonDecoder.gen[OrderInstrumentRequest]
  implicit val encoder: JsonEncoder[OrderInstrumentRequest] = DeriveJsonEncoder.gen[OrderInstrumentRequest]
}