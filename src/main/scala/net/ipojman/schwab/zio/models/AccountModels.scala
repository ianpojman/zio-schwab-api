package net.ipojman.schwab.zio.models

import zio.json.*

// Account summary returned from GET /trader/v1/accounts
case class SchwabAccountSummary(
  accountNumber: String,
  `type`: String,
  nickname: Option[String]
)

object SchwabAccountSummary {
  implicit val decoder: JsonDecoder[SchwabAccountSummary] = DeriveJsonDecoder.gen[SchwabAccountSummary]
  implicit val encoder: JsonEncoder[SchwabAccountSummary] = DeriveJsonEncoder.gen[SchwabAccountSummary]
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
  currentBalances: CurrentBalances,
  positions: List[SchwabPosition]
)

object SecuritiesAccount {
  implicit val decoder: JsonDecoder[SecuritiesAccount] = DeriveJsonDecoder.gen[SecuritiesAccount]
  implicit val encoder: JsonEncoder[SecuritiesAccount] = DeriveJsonEncoder.gen[SecuritiesAccount]
}

case class CurrentBalances(
  cashBalance: Double,
  totalCash: Double,
  netLiquidation: Double
)

object CurrentBalances {
  implicit val decoder: JsonDecoder[CurrentBalances] = DeriveJsonDecoder.gen[CurrentBalances]
  implicit val encoder: JsonEncoder[CurrentBalances] = DeriveJsonEncoder.gen[CurrentBalances]
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