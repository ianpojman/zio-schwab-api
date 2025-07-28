package net.ipojman.schwab.zio.models

import zio.json.*

// Quote wrapper that matches the actual API response structure
case class QuoteResponse(
  assetMainType: Option[String] = None,
  assetSubType: Option[String] = None,
  quoteType: Option[String] = None,
  symbol: String,
  realtime: Option[Boolean] = None,
  ssid: Option[Long] = None,
  reference: Option[Reference] = None,
  quote: Option[QuoteData] = None,
  regular: Option[RegularMarket] = None,
  extended: Option[ExtendedMarket] = None,
  fundamental: Option[Fundamental] = None
)

object QuoteResponse {
  implicit val decoder: JsonDecoder[QuoteResponse] = DeriveJsonDecoder.gen[QuoteResponse]
  implicit val encoder: JsonEncoder[QuoteResponse] = DeriveJsonEncoder.gen[QuoteResponse]
}

case class Reference(
  cusip: Option[String] = None,
  description: Option[String] = None,
  exchange: Option[String] = None,
  exchangeName: Option[String] = None,
  contractType: Option[String] = None,
  daysToExpiration: Option[Int] = None,
  expirationDay: Option[Int] = None,
  expirationMonth: Option[Int] = None,
  expirationYear: Option[Int] = None,
  isPennyPilot: Option[Boolean] = None,
  lastTradingDay: Option[Long] = None,
  multiplier: Option[Int] = None,
  settlementType: Option[String] = None,
  strikePrice: Option[Double] = None,
  underlying: Option[String] = None,
  uvExpirationType: Option[String] = None,
  otcMarketTier: Option[String] = None,
  futureActiveSymbol: Option[String] = None,
  futureExpirationDate: Option[Long] = None,
  futureIsActive: Option[Boolean] = None,
  futureIsTradable: Option[Boolean] = None,
  futureMultiplier: Option[Int] = None,
  futurePriceFormat: Option[String] = None,
  futureSettlementPrice: Option[Double] = None,
  futureTradingHours: Option[String] = None,
  product: Option[String] = None,
  isTradable: Option[Boolean] = None,
  marketMaker: Option[String] = None,
  tradingHours: Option[String] = None
)

object Reference {
  implicit val decoder: JsonDecoder[Reference] = DeriveJsonDecoder.gen[Reference]
  implicit val encoder: JsonEncoder[Reference] = DeriveJsonEncoder.gen[Reference]
}

case class QuoteData(
  `52WeekHigh`: Option[Double] = None,
  `52WeekLow`: Option[Double] = None,
  askMICId: Option[String] = None,
  askPrice: Option[Double] = None,
  askSize: Option[Int] = None,
  askTime: Option[Long] = None,
  bidMICId: Option[String] = None,
  bidPrice: Option[Double] = None,
  bidSize: Option[Int] = None,
  bidTime: Option[Long] = None,
  closePrice: Option[Double] = None,
  highPrice: Option[Double] = None,
  lastMICId: Option[String] = None,
  lastPrice: Option[Double] = None,
  lastSize: Option[Int] = None,
  lowPrice: Option[Double] = None,
  mark: Option[Double] = None,
  markChange: Option[Double] = None,
  markPercentChange: Option[Double] = None,
  netChange: Option[Double] = None,
  netPercentChange: Option[Double] = None,
  openPrice: Option[Double] = None,
  quoteTime: Option[Long] = None,
  securityStatus: Option[String] = None,
  totalVolume: Option[Long] = None,
  tradeTime: Option[Long] = None,
  volatility: Option[Double] = None,
  openInterest: Option[Int] = None,
  moneyIntrinsicValue: Option[Double] = None,
  timeValue: Option[Double] = None,
  delta: Option[Double] = None,
  gamma: Option[Double] = None,
  theta: Option[Double] = None,
  vega: Option[Double] = None,
  rho: Option[Double] = None,
  theoreticalOptionValue: Option[Double] = None,
  underlyingPrice: Option[Double] = None,
  impliedYield: Option[Double] = None,
  indAskPrice: Option[Double] = None,
  indBidPrice: Option[Double] = None,
  indQuoteTime: Option[Long] = None,
  futurePercentChange: Option[Double] = None,
  settleTime: Option[Long] = None,
  tick: Option[Double] = None,
  tickAmount: Option[Double] = None,
  nAV: Option[Double] = None
)

object QuoteData {
  implicit val decoder: JsonDecoder[QuoteData] = DeriveJsonDecoder.gen[QuoteData]
  implicit val encoder: JsonEncoder[QuoteData] = DeriveJsonEncoder.gen[QuoteData]
}

case class RegularMarket(
  regularMarketLastPrice: Option[Double] = None,
  regularMarketLastSize: Option[Int] = None,
  regularMarketNetChange: Option[Double] = None,
  regularMarketPercentChange: Option[Double] = None,
  regularMarketTradeTime: Option[Long] = None
)

object RegularMarket {
  implicit val decoder: JsonDecoder[RegularMarket] = DeriveJsonDecoder.gen[RegularMarket]
  implicit val encoder: JsonEncoder[RegularMarket] = DeriveJsonEncoder.gen[RegularMarket]
}

case class ExtendedMarket(
  askPrice: Option[Double] = None,
  askSize: Option[Int] = None,
  bidPrice: Option[Double] = None,
  bidSize: Option[Int] = None,
  lastPrice: Option[Double] = None,
  lastSize: Option[Int] = None,
  mark: Option[Double] = None,
  quoteTime: Option[Long] = None,
  totalVolume: Option[Long] = None,
  tradeTime: Option[Long] = None
)

object ExtendedMarket {
  implicit val decoder: JsonDecoder[ExtendedMarket] = DeriveJsonDecoder.gen[ExtendedMarket]
  implicit val encoder: JsonEncoder[ExtendedMarket] = DeriveJsonEncoder.gen[ExtendedMarket]
}

case class Fundamental(
  avg10DaysVolume: Option[Double] = None,
  avg1YearVolume: Option[Double] = None,
  declarationDate: Option[String] = None,
  divAmount: Option[Double] = None,
  divExDate: Option[String] = None,
  divFreq: Option[Int] = None,
  divPayAmount: Option[Double] = None,
  divPayDate: Option[String] = None,
  divYield: Option[Double] = None,
  eps: Option[Double] = None,
  fundLeverageFactor: Option[Double] = None,
  nextDivExDate: Option[String] = None,
  nextDivPayDate: Option[String] = None,
  peRatio: Option[Double] = None,
  fundStrategy: Option[String] = None
)

object Fundamental {
  implicit val decoder: JsonDecoder[Fundamental] = DeriveJsonDecoder.gen[Fundamental]
  implicit val encoder: JsonEncoder[Fundamental] = DeriveJsonEncoder.gen[Fundamental]
}

// Simplified Quote model for easier use
case class Quote(
  symbol: String,
  bid: Option[Double] = None,
  ask: Option[Double] = None,
  last: Option[Double] = None,
  open: Option[Double] = None,
  high: Option[Double] = None,
  low: Option[Double] = None,
  close: Option[Double] = None,
  volume: Option[Long] = None,
  quoteTime: Option[Long] = None,
  bidSize: Option[Int] = None,
  askSize: Option[Int] = None,
  mark: Option[Double] = None,
  change: Option[Double] = None,
  percentChange: Option[Double] = None,
  fiftyTwoWeekHigh: Option[Double] = None,
  fiftyTwoWeekLow: Option[Double] = None,
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
  underlying: Option[String] = None,
  strategy: String,
  interval: Double,
  isDelayed: Boolean,
  isIndex: Boolean,
  daysToExpiration: Double,
  interestRate: Double,
  underlyingPrice: Double,
  volatility: Double,
  dividendYield: Option[Double] = None,
  numberOfContracts: Option[Int] = None,
  assetMainType: Option[String] = None,
  assetSubType: Option[String] = None,
  isChainTruncated: Option[Boolean] = None,
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
  bid: Option[Double] = None,
  ask: Option[Double] = None,
  last: Option[Double] = None,
  mark: Option[Double] = None,
  bidSize: Option[Int] = None,
  askSize: Option[Int] = None,
  bidAskSize: Option[String] = None,
  lastSize: Option[Int] = None,
  highPrice: Option[Double] = None,
  lowPrice: Option[Double] = None,
  openPrice: Option[Double] = None,
  closePrice: Option[Double] = None,
  totalVolume: Option[Long] = None,
  quoteTimeInLong: Option[Long] = None,
  tradeTimeInLong: Option[Long] = None,
  netChange: Option[Double] = None,
  volatility: Option[Double] = None,
  delta: Option[Double] = None,
  gamma: Option[Double] = None,
  theta: Option[Double] = None,
  vega: Option[Double] = None,
  rho: Option[Double] = None,
  timeValue: Option[Double] = None,
  openInterest: Option[Int] = None,
  isInTheMoney: Option[Boolean] = None,
  theoreticalOptionValue: Option[Double] = None,
  theoreticalVolatility: Option[Double] = None,
  isMini: Option[Boolean] = None,
  isNonStandard: Option[Boolean] = None,
  optionDeliverablesList: Option[List[OptionDeliverable]] = None,
  strikePrice: Option[Double] = None,
  expirationDate: Option[String] = None,
  expirationType: Option[String] = None,
  multiplier: Option[Double] = None,
  settlementType: Option[String] = None,
  deliverableNote: Option[String] = None,
  isIndexOption: Option[Boolean] = None,
  percentChange: Option[Double] = None,
  markChange: Option[Double] = None,
  markPercentChange: Option[Double] = None,
  isPennyPilot: Option[Boolean] = None
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
case class MoverResponse(
  screeners: List[Mover]
)

object MoverResponse {
  implicit val decoder: JsonDecoder[MoverResponse] = DeriveJsonDecoder.gen[MoverResponse]
  implicit val encoder: JsonEncoder[MoverResponse] = DeriveJsonEncoder.gen[MoverResponse]
}

case class Mover(
  symbol: String,
  description: String,
  lastPrice: Option[Double] = None,
  netChange: Option[Double] = None,
  netPercentChange: Option[Double] = None,
  volume: Option[Long] = None,
  marketShare: Option[Double] = None,
  totalVolume: Option[Long] = None,
  trades: Option[Int] = None,
  // Legacy fields for backward compatibility
  last: Option[Double] = None,
  change: Option[Double] = None,
  percentChange: Option[Double] = None
)

object Mover {
  implicit val decoder: JsonDecoder[Mover] = DeriveJsonDecoder.gen[Mover]
  implicit val encoder: JsonEncoder[Mover] = DeriveJsonEncoder.gen[Mover]
}

// Market Hours Models
case class MarketHoursDetail(
  date: String,
  marketType: String,
  product: String,
  isOpen: Boolean,
  sessionHours: Option[SessionHours] = None
)

object MarketHoursDetail {
  implicit val decoder: JsonDecoder[MarketHoursDetail] = DeriveJsonDecoder.gen[MarketHoursDetail]
  implicit val encoder: JsonEncoder[MarketHoursDetail] = DeriveJsonEncoder.gen[MarketHoursDetail]
}

case class MarketHours(
  option: Option[Map[String, MarketHoursDetail]] = None,
  equity: Option[Map[String, MarketHoursDetail]] = None,
  forex: Option[Map[String, MarketHoursDetail]] = None,
  future: Option[Map[String, MarketHoursDetail]] = None,
  bond: Option[Map[String, MarketHoursDetail]] = None
)

object MarketHours {
  implicit val decoder: JsonDecoder[MarketHours] = DeriveJsonDecoder.gen[MarketHours]
  implicit val encoder: JsonEncoder[MarketHours] = DeriveJsonEncoder.gen[MarketHours]
}

case class SessionHours(
  preMarket: Option[List[MarketSession]] = None,
  regularMarket: Option[List[MarketSession]] = None,
  postMarket: Option[List[MarketSession]] = None
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
  fundamental: Option[Fundamental] = None
)

object InstrumentResponse {
  implicit val decoder: JsonDecoder[InstrumentResponse] = DeriveJsonDecoder.gen[InstrumentResponse]
  implicit val encoder: JsonEncoder[InstrumentResponse] = DeriveJsonEncoder.gen[InstrumentResponse]
}

case class InstrumentsResponse(
  instruments: List[InstrumentResponse]
)

object InstrumentsResponse {
  implicit val decoder: JsonDecoder[InstrumentsResponse] = DeriveJsonDecoder.gen[InstrumentsResponse]
  implicit val encoder: JsonEncoder[InstrumentsResponse] = DeriveJsonEncoder.gen[InstrumentsResponse]
}

// Old Fundamentals class - commented out as it doesn't match the actual API response
// The API returns a simpler 'fundamental' object with different field names
/*
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
*/

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