package net.ipojman.schwab.zio.models

import zio.json.*

// Quote Models
case class Quote(
  symbol: String,
  bid: Double,
  ask: Double,
  last: Double,
  open: Double,
  high: Double,
  low: Double,
  close: Double,
  volume: Long,
  quoteTime: Long,
  bidSize: Int,
  askSize: Int,
  mark: Double,
  change: Double,
  percentChange: Double,
  fiftyTwoWeekHigh: Double,
  fiftyTwoWeekLow: Double,
  openInterest: Option[Int] = None,
  volatility: Option[Double] = None,
  moneyIntrinsicValue: Option[Double] = None,
  timeValue: Option[Double] = None,
  delta: Option[Double] = None,
  gamma: Option[Double] = None,
  theta: Option[Double] = None,
  vega: Option[Double] = None,
  rho: Option[Double] = None,
  theoreticalOptionValue: Option[Double] = None,
  underlyingPrice: Option[Double] = None,
  `52WeekHigh`: Option[Double] = None,
  `52WeekLow`: Option[Double] = None
)

object Quote {
  implicit val decoder: JsonDecoder[Quote] = DeriveJsonDecoder.gen[Quote]
  implicit val encoder: JsonEncoder[Quote] = DeriveJsonEncoder.gen[Quote]
}

// Option Chain Models
case class OptionChain(
  symbol: String,
  status: String,
  strategy: String,
  interval: Double,
  isDelayed: Boolean,
  isIndex: Boolean,
  daysToExpiration: Int,
  interestRate: Double,
  underlyingPrice: Double,
  volatility: Double,
  callExpDateMap: Map[String, Map[String, List[OptionContract]]],
  putExpDateMap: Map[String, Map[String, List[OptionContract]]]
)

object OptionChain {
  implicit val decoder: JsonDecoder[OptionChain] = DeriveJsonDecoder.gen[OptionChain]
  implicit val encoder: JsonEncoder[OptionChain] = DeriveJsonEncoder.gen[OptionChain]
}

case class OptionContract(
  symbol: String,
  putCall: String,
  description: String,
  exchangeName: String,
  bid: Double,
  ask: Double,
  last: Double,
  mark: Double,
  bidSize: Int,
  askSize: Int,
  lastSize: Int,
  highPrice: Double,
  lowPrice: Double,
  openPrice: Double,
  closePrice: Double,
  totalVolume: Long,
  quoteTimeInLong: Long,
  tradeTimeInLong: Long,
  netChange: Double,
  volatility: Double,
  delta: Double,
  gamma: Double,
  theta: Double,
  vega: Double,
  rho: Double,
  timeValue: Double,
  openInterest: Int,
  isInTheMoney: Boolean,
  theoreticalOptionValue: Double,
  theoreticalVolatility: Double,
  isMini: Boolean,
  isNonStandard: Boolean,
  optionDeliverablesList: Option[List[OptionDeliverable]] = None,
  strikePrice: Double,
  expirationDate: String,
  expirationType: String,
  multiplier: Double,
  settlementType: String,
  deliverableNote: String,
  isIndexOption: Boolean,
  percentChange: Double,
  markChange: Double,
  markPercentChange: Double,
  isPennyPilot: Boolean
)

object OptionContract {
  implicit val decoder: JsonDecoder[OptionContract] = DeriveJsonDecoder.gen[OptionContract]
  implicit val encoder: JsonEncoder[OptionContract] = DeriveJsonEncoder.gen[OptionContract]
}

case class OptionDeliverable(
  symbol: String,
  assetType: String,
  deliverableUnits: Double,
  currencyType: Option[String] = None
)

object OptionDeliverable {
  implicit val decoder: JsonDecoder[OptionDeliverable] = DeriveJsonDecoder.gen[OptionDeliverable]
  implicit val encoder: JsonEncoder[OptionDeliverable] = DeriveJsonEncoder.gen[OptionDeliverable]
}

// Price History Models
case class PriceHistory(
  symbol: String,
  empty: Boolean,
  candles: List[Candle]
)

object PriceHistory {
  implicit val decoder: JsonDecoder[PriceHistory] = DeriveJsonDecoder.gen[PriceHistory]
  implicit val encoder: JsonEncoder[PriceHistory] = DeriveJsonEncoder.gen[PriceHistory]
}

case class Candle(
  datetime: Long,
  open: Double,
  high: Double,
  low: Double,
  close: Double,
  volume: Long
)

object Candle {
  implicit val decoder: JsonDecoder[Candle] = DeriveJsonDecoder.gen[Candle]
  implicit val encoder: JsonEncoder[Candle] = DeriveJsonEncoder.gen[Candle]
}

// Movers Models
case class Mover(
  symbol: String,
  description: String,
  last: Double,
  change: Double,
  percentChange: Double,
  totalVolume: Long
)

object Mover {
  implicit val decoder: JsonDecoder[Mover] = DeriveJsonDecoder.gen[Mover]
  implicit val encoder: JsonEncoder[Mover] = DeriveJsonEncoder.gen[Mover]
}

// Market Hours Models
case class MarketHours(
  date: String,
  marketType: String,
  exchange: String,
  category: String,
  product: String,
  productName: String,
  isOpen: Boolean,
  sessionHours: Option[SessionHours] = None
)

object MarketHours {
  implicit val decoder: JsonDecoder[MarketHours] = DeriveJsonDecoder.gen[MarketHours]
  implicit val encoder: JsonEncoder[MarketHours] = DeriveJsonEncoder.gen[MarketHours]
}

case class SessionHours(
  preMarket: List[MarketSession],
  regularMarket: List[MarketSession],
  postMarket: List[MarketSession]
)

object SessionHours {
  implicit val decoder: JsonDecoder[SessionHours] = DeriveJsonDecoder.gen[SessionHours]
  implicit val encoder: JsonEncoder[SessionHours] = DeriveJsonEncoder.gen[SessionHours]
}

case class MarketSession(
  start: String,
  end: String
)

object MarketSession {
  implicit val decoder: JsonDecoder[MarketSession] = DeriveJsonDecoder.gen[MarketSession]
  implicit val encoder: JsonEncoder[MarketSession] = DeriveJsonEncoder.gen[MarketSession]
}

// Instrument Models
case class InstrumentResponse(
  symbol: String,
  cusip: String,
  description: String,
  exchange: String,
  assetType: String,
  fundamentals: Option[Fundamentals] = None
)

object InstrumentResponse {
  implicit val decoder: JsonDecoder[InstrumentResponse] = DeriveJsonDecoder.gen[InstrumentResponse]
  implicit val encoder: JsonEncoder[InstrumentResponse] = DeriveJsonEncoder.gen[InstrumentResponse]
}

case class Fundamentals(
  high52: Double,
  low52: Double,
  dividendAmount: Double,
  dividendYield: Double,
  dividendDate: String,
  peRatio: Double,
  pegRatio: Double,
  pbRatio: Double,
  prRatio: Double,
  pcfRatio: Double,
  grossMarginTTM: Double,
  grossMarginMRQ: Double,
  netProfitMarginTTM: Double,
  netProfitMarginMRQ: Double,
  operatingMarginTTM: Double,
  operatingMarginMRQ: Double,
  returnOnEquity: Double,
  returnOnAssets: Double,
  returnOnInvestment: Double,
  quickRatio: Double,
  currentRatio: Double,
  interestCoverage: Double,
  totalDebtToCapital: Double,
  ltDebtToEquity: Double,
  totalDebtToEquity: Double,
  epsTTM: Double,
  epsChangePercentTTM: Double,
  epsChangeYear: Double,
  epsChange: Double,
  revChangeYear: Double,
  revChangeTTM: Double,
  revChangeIn: Double,
  sharesOutstanding: Double,
  marketCapFloat: Double,
  marketCap: Double,
  bookValuePerShare: Double,
  shortIntToFloat: Double,
  shortIntDayToCover: Double,
  divGrowthRate3Year: Double,
  dividendPayAmount: Double,
  dividendPayDate: String,
  beta: Double,
  vol1DayAvg: Double,
  vol10DayAvg: Double,
  vol3MonthAvg: Double
)

object Fundamentals {
  implicit val decoder: JsonDecoder[Fundamentals] = DeriveJsonDecoder.gen[Fundamentals]
  implicit val encoder: JsonEncoder[Fundamentals] = DeriveJsonEncoder.gen[Fundamentals]
}

// Option Expiration Models
case class ExpirationDate(
  expirationDate: String,
  daysToExpiration: Int,
  expirationType: String
)

object ExpirationDate {
  implicit val decoder: JsonDecoder[ExpirationDate] = DeriveJsonDecoder.gen[ExpirationDate]
  implicit val encoder: JsonEncoder[ExpirationDate] = DeriveJsonEncoder.gen[ExpirationDate]
}

case class OptionExpirationChain(
  symbol: String,
  status: String,
  expirationList: List[ExpirationDate]
)

object OptionExpirationChain {
  implicit val decoder: JsonDecoder[OptionExpirationChain] = DeriveJsonDecoder.gen[OptionExpirationChain]
  implicit val encoder: JsonEncoder[OptionExpirationChain] = DeriveJsonEncoder.gen[OptionExpirationChain]
}