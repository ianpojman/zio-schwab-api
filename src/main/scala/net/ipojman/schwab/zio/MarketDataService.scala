package net.ipojman.schwab.zio

import zio.*
import net.ipojman.schwab.zio.models.*

/**
 * Service interface for Schwab Market Data API
 * 
 * Base URL: https://api.schwabapi.com/marketdata/v1
 */
trait MarketDataService {
  
  /**
   * Get quotes for multiple symbols
   * GET /quotes
   * 
   * @param symbols List of symbols to get quotes for
   * @param fields Optional fields to include (e.g., "quote", "fundamental", "extended", "reference", "regular")
   * @param indicative Include indicative symbol quotes (default: false)
   * @return Map of symbol to Quote
   */
  def getQuotes(
    symbols: List[String], 
    fields: Option[String] = None,
    indicative: Boolean = false
  ): Task[Map[String, Quote]]
  
  /**
   * Get quote for a single symbol
   * GET /{symbol_id}/quotes
   * 
   * @param symbol Symbol to get quote for
   * @param fields Optional fields to include
   * @return Quote for the symbol
   */
  def getQuote(
    symbol: String,
    fields: Option[String] = None
  ): Task[Quote]
  
  /**
   * Get option chain for an optionable symbol
   * GET /chains
   * 
   * @param symbol Underlying symbol
   * @param contractType Type of contracts (CALL, PUT, ALL)
   * @param strikeCount Number of strikes above and below at-the-money price
   * @param includeQuotes Include quotes for options (default: false)
   * @param strategy Option strategy (SINGLE, ANALYTICAL, COVERED, VERTICAL, CALENDAR, STRANGLE, STRADDLE, BUTTERFLY, CONDOR, DIAGONAL, COLLAR, ROLL)
   * @param interval Strike interval for spread strategy chains
   * @param strike Strike price
   * @param range Option chain range (ITM, NTM, OTM, SAB, SBL, SNK, ALL)
   * @param fromDate From expiration date (yyyy-MM-dd)
   * @param toDate To expiration date (yyyy-MM-dd)
   * @param volatility Volatility for theoretical values
   * @param underlyingPrice Underlying price for theoretical values
   * @param interestRate Interest rate for theoretical values
   * @param daysToExpiration Days to expiration for theoretical values
   * @param expMonth Expiration month
   * @param optionType Option type (S for Standard, NS for Non-Standard, ALL)
   * @return Option chain data
   */
  def getOptionChain(
    symbol: String,
    contractType: Option[String] = None,
    strikeCount: Option[Int] = None,
    includeQuotes: Boolean = false,
    strategy: Option[String] = Some("SINGLE"),
    interval: Option[Double] = None,
    strike: Option[Double] = None,
    range: Option[String] = None,
    fromDate: Option[String] = None,
    toDate: Option[String] = None,
    volatility: Option[Double] = None,
    underlyingPrice: Option[Double] = None,
    interestRate: Option[Double] = None,
    daysToExpiration: Option[Int] = None,
    expMonth: Option[String] = None,
    optionType: Option[String] = None
  ): Task[OptionChain]
  
  /**
   * Get option expiration chain for an optionable symbol
   * GET /expirationchain
   * 
   * @param symbol Underlying symbol
   * @return Option expiration chain data
   */
  def getOptionExpirationChain(symbol: String): Task[OptionExpirationChain]
  
  /**
   * Get price history for a single symbol
   * GET /pricehistory
   * 
   * @param symbol Symbol to get history for
   * @param periodType Type of period (day, month, year, ytd)
   * @param period Number of periods
   * @param frequencyType Type of frequency (minute, daily, weekly, monthly)
   * @param frequency Frequency amount
   * @param startDate Start date (epoch milliseconds)
   * @param endDate End date (epoch milliseconds)
   * @param needExtendedHoursData Include extended hours data (default: true)
   * @return Price history data
   */
  def getPriceHistory(
    symbol: String,
    periodType: Option[String] = None,
    period: Option[Int] = None,
    frequencyType: Option[String] = None,
    frequency: Option[Int] = None,
    startDate: Option[Long] = None,
    endDate: Option[Long] = None,
    needExtendedHoursData: Boolean = true
  ): Task[PriceHistory]
  
  /**
   * Get movers for a specific index
   * GET /movers/{symbol_id}
   * 
   * @param index Index symbol ($DJI, $COMPX, $SPX, NYSE, NASDAQ, OTCBB, INDEX_ALL, EQUITY_ALL, OPTION_ALL, OPTION_PUT, OPTION_CALL)
   * @param sort Sort direction (VOLUME, TRADES, PERCENT_CHANGE_UP, PERCENT_CHANGE_DOWN)
   * @param frequency Frequency (0, 1, 5, 10, 30, 60)
   * @return List of movers
   */
  def getMovers(
    index: String,
    sort: Option[String] = None,
    frequency: Option[Int] = None
  ): Task[List[Mover]]
  
  /**
   * Get market hours for different markets
   * GET /markets
   * 
   * @param markets List of markets (EQUITY, OPTION, BOND, FUTURE, FOREX)
   * @param date Date in yyyy-MM-dd format
   * @return Market hours grouped by market type
   */
  def getMarketHours(
    markets: List[String],
    date: Option[String] = None
  ): Task[MarketHours]
  
  /**
   * Get market hours for a single market
   * GET /markets/{market_id}
   * 
   * @param market Market type (EQUITY, OPTION, BOND, FUTURE, FOREX)
   * @param date Date in yyyy-MM-dd format
   * @return Market hours
   */
  def getMarketHour(
    market: String,
    date: Option[String] = None
  ): Task[MarketHours]
  
  /**
   * Get instruments by symbols and projections
   * GET /instruments
   * 
   * @param symbol Symbol to search for
   * @param projection Type of request (symbol-search, symbol-regex, desc-search, desc-regex, search, fundamental)
   * @return List of instruments
   */
  def getInstruments(
    symbol: String,
    projection: String
  ): Task[List[InstrumentResponse]]
  
  /**
   * Get instrument by specific cusip
   * GET /instruments/{cusip_id}
   * 
   * @param cusip CUSIP identifier
   * @return Instrument data
   */
  def getInstrumentByCusip(cusip: String): Task[InstrumentResponse]
}

object MarketDataService {
  def getQuotes(
    symbols: List[String], 
    fields: Option[String] = None,
    indicative: Boolean = false
  ): ZIO[MarketDataService, Throwable, Map[String, Quote]] =
    ZIO.serviceWithZIO[MarketDataService](_.getQuotes(symbols, fields, indicative))
    
  def getQuote(
    symbol: String,
    fields: Option[String] = None
  ): ZIO[MarketDataService, Throwable, Quote] =
    ZIO.serviceWithZIO[MarketDataService](_.getQuote(symbol, fields))
    
  def getOptionChain(
    symbol: String,
    contractType: Option[String] = None,
    strikeCount: Option[Int] = None,
    includeQuotes: Boolean = false,
    strategy: Option[String] = Some("SINGLE"),
    interval: Option[Double] = None,
    strike: Option[Double] = None,
    range: Option[String] = None,
    fromDate: Option[String] = None,
    toDate: Option[String] = None,
    volatility: Option[Double] = None,
    underlyingPrice: Option[Double] = None,
    interestRate: Option[Double] = None,
    daysToExpiration: Option[Int] = None,
    expMonth: Option[String] = None,
    optionType: Option[String] = None
  ): ZIO[MarketDataService, Throwable, OptionChain] =
    ZIO.serviceWithZIO[MarketDataService](_.getOptionChain(
      symbol, contractType, strikeCount, includeQuotes, strategy, interval,
      strike, range, fromDate, toDate, volatility, underlyingPrice,
      interestRate, daysToExpiration, expMonth, optionType
    ))
    
  def getOptionExpirationChain(symbol: String): ZIO[MarketDataService, Throwable, OptionExpirationChain] =
    ZIO.serviceWithZIO[MarketDataService](_.getOptionExpirationChain(symbol))
    
  def getPriceHistory(
    symbol: String,
    periodType: Option[String] = None,
    period: Option[Int] = None,
    frequencyType: Option[String] = None,
    frequency: Option[Int] = None,
    startDate: Option[Long] = None,
    endDate: Option[Long] = None,
    needExtendedHoursData: Boolean = true
  ): ZIO[MarketDataService, Throwable, PriceHistory] =
    ZIO.serviceWithZIO[MarketDataService](_.getPriceHistory(
      symbol, periodType, period, frequencyType, frequency,
      startDate, endDate, needExtendedHoursData
    ))
    
  def getMovers(
    index: String,
    sort: Option[String] = None,
    frequency: Option[Int] = None
  ): ZIO[MarketDataService, Throwable, List[Mover]] =
    ZIO.serviceWithZIO[MarketDataService](_.getMovers(index, sort, frequency))
    
  def getMarketHours(
    markets: List[String],
    date: Option[String] = None
  ): ZIO[MarketDataService, Throwable, MarketHours] =
    ZIO.serviceWithZIO[MarketDataService](_.getMarketHours(markets, date))
    
  def getMarketHour(
    market: String,
    date: Option[String] = None
  ): ZIO[MarketDataService, Throwable, MarketHours] =
    ZIO.serviceWithZIO[MarketDataService](_.getMarketHour(market, date))
    
  def getInstruments(
    symbol: String,
    projection: String
  ): ZIO[MarketDataService, Throwable, List[InstrumentResponse]] =
    ZIO.serviceWithZIO[MarketDataService](_.getInstruments(symbol, projection))
    
  def getInstrumentByCusip(cusip: String): ZIO[MarketDataService, Throwable, InstrumentResponse] =
    ZIO.serviceWithZIO[MarketDataService](_.getInstrumentByCusip(cusip))
}