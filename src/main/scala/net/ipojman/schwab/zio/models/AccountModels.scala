package net.ipojman.schwab.zio.models

import zio.json.*

// Response item from GET /trader/v1/accounts
// Each item in the array has a securitiesAccount field
case class SchwabAccountListItem(
  securitiesAccount: SecuritiesAccount
)

object SchwabAccountListItem {
  implicit val decoder: JsonDecoder[SchwabAccountListItem] = DeriveJsonDecoder.gen[SchwabAccountListItem]
  implicit val encoder: JsonEncoder[SchwabAccountListItem] = DeriveJsonEncoder.gen[SchwabAccountListItem]
}

// Full account response from GET /trader/v1/accounts/{accountNumber}
case class SchwabAccountResponse(
  securitiesAccount: SecuritiesAccount
)

object SchwabAccountResponse {
  implicit val decoder: JsonDecoder[SchwabAccountResponse] = DeriveJsonDecoder.gen[SchwabAccountResponse]
  implicit val encoder: JsonEncoder[SchwabAccountResponse] = DeriveJsonEncoder.gen[SchwabAccountResponse]
}

case class SecuritiesAccount(
  accountNumber: String,
  `type`: String = "CASH",
  roundTrips: Option[Int] = None,
  isDayTrader: Option[Boolean] = None,
  isClosingOnlyRestricted: Option[Boolean] = None,
  pfcbFlag: Option[Boolean] = None,
  positions: Option[List[SchwabPosition]] = None,
  initialBalances: Option[InitialBalances] = None,
  currentBalances: Option[CurrentBalances] = None,
  projectedBalances: Option[ProjectedBalances] = None
)

object SecuritiesAccount {
  implicit val decoder: JsonDecoder[SecuritiesAccount] = DeriveJsonDecoder.gen[SecuritiesAccount]
  implicit val encoder: JsonEncoder[SecuritiesAccount] = DeriveJsonEncoder.gen[SecuritiesAccount]
}

case class CurrentBalances(
  cashBalance: Option[Double] = None,
  totalCash: Option[Double] = None,
  netLiquidation: Option[Double] = None,
  availableFunds: Option[Double] = None,
  buyingPower: Option[Double] = None,
  equity: Option[Double] = None,
  marginBalance: Option[Double] = None
)

object CurrentBalances {
  implicit val decoder: JsonDecoder[CurrentBalances] = DeriveJsonDecoder.gen[CurrentBalances]
  implicit val encoder: JsonEncoder[CurrentBalances] = DeriveJsonEncoder.gen[CurrentBalances]
}

// Initial balances typically have more fields including totalCash
case class InitialBalances(
  cashBalance: Option[Double] = None,
  totalCash: Option[Double] = None,
  equity: Option[Double] = None,
  buyingPower: Option[Double] = None,
  availableFundsNonMarginableTrade: Option[Double] = None,
  liquidationValue: Option[Double] = None,
  accountValue: Option[Double] = None
)

object InitialBalances {
  implicit val decoder: JsonDecoder[InitialBalances] = DeriveJsonDecoder.gen[InitialBalances]
  implicit val encoder: JsonEncoder[InitialBalances] = DeriveJsonEncoder.gen[InitialBalances]
}

case class ProjectedBalances(
  cashBalance: Option[Double] = None,
  equity: Option[Double] = None
)

object ProjectedBalances {
  implicit val decoder: JsonDecoder[ProjectedBalances] = DeriveJsonDecoder.gen[ProjectedBalances]
  implicit val encoder: JsonEncoder[ProjectedBalances] = DeriveJsonEncoder.gen[ProjectedBalances]
}

case class SchwabPosition(
  instrument: Instrument,
  longQuantity: Double,
  marketValue: Double,
  averagePrice: Double,
  currentDayProfitLoss: Option[Double],
  currentDayProfitLossPercentage: Option[Double]
)

object SchwabPosition {
  implicit val decoder: JsonDecoder[SchwabPosition] = DeriveJsonDecoder.gen[SchwabPosition]
  implicit val encoder: JsonEncoder[SchwabPosition] = DeriveJsonEncoder.gen[SchwabPosition]
}

case class Instrument(
  symbol: String,
  description: Option[String],
  assetType: String
)

object Instrument {
  implicit val decoder: JsonDecoder[Instrument] = DeriveJsonDecoder.gen[Instrument]
  implicit val encoder: JsonEncoder[Instrument] = DeriveJsonEncoder.gen[Instrument]
}