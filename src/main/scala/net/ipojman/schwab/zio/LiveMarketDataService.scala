package net.ipojman.schwab.zio

import zio.*
import zio.http.*
import zio.json.*
import net.ipojman.schwab.zio.models.*

/**
 * Live implementation of MarketDataService that uses the SchwabClient
 */
case class LiveMarketDataService(client: SchwabClient) extends MarketDataService {
  
  private val baseEndpoint = "marketdata/v1"
  
  private def buildQueryParams(params: (String, Option[Any])*): String = {
    val validParams = params.collect {
      case (key, Some(value)) => s"$key=$value"
    }
    if (validParams.isEmpty) "" else "?" + validParams.mkString("&")
  }
  
  /**
   * Get a valid token - uses ensureAuthenticated for SeamlessSchwabClient,
   * falls back to getAccessToken for other implementations
   */
  private def getValidToken(): Task[TokenResponse] = {
    client match {
      case seamless: SeamlessSchwabClient => seamless.ensureAuthenticated()
      case _ => client.getAccessToken
    }
  }
  
  override def getQuotes(
    symbols: List[String], 
    fields: Option[String] = None,
    indicative: Boolean = false
  ): Task[Map[String, Quote]] = {
    val symbolsParam = symbols.mkString(",")
    val queryParams = buildQueryParams(
      "symbols" -> Some(symbolsParam),
      "fields" -> fields,
      "indicative" -> (if (indicative) Some(true) else None)
    )
    
    val endpoint = s"$baseEndpoint/quotes$queryParams"
    
    val apiCall = client match {
      case seamless: SeamlessSchwabClient =>
        // Use the safe retry mechanism for seamless clients
        seamless.makeApiCallSafeWithRetry[Map[String, QuoteResponse]](endpoint)
      case _ =>
        // Fallback to safe standard approach for other clients
        for {
          token <- getValidToken()
          response <- client.makeApiCallSafe[Map[String, QuoteResponse]](endpoint, token.access_token)
        } yield response
    }
    
    for {
      apiResponse <- apiCall
      response <- apiResponse match {
        case SchwabApiResponse.Success(data) => ZIO.succeed(data)
        case SchwabApiResponse.Error(errors) =>
          val errorMessages = errors.map(_.message).mkString(", ")
          ZIO.fail(new RuntimeException(s"Schwab API error for symbols ${symbols.mkString(",")}: $errorMessages"))
      }
      // Convert QuoteResponse to simplified Quote
      simplifiedQuotes = response.map { case (symbol, qr) =>
        val qd = qr.quote
        val ext = qr.extended
        val reg = qr.regular
        
        // Use quote data first, then extended, then regular market data as fallback
        symbol -> Quote(
          symbol = symbol,
          bid = qd.flatMap(_.bidPrice).orElse(ext.flatMap(_.bidPrice)),
          ask = qd.flatMap(_.askPrice).orElse(ext.flatMap(_.askPrice)),
          last = qd.flatMap(_.lastPrice).orElse(ext.flatMap(_.lastPrice)).orElse(reg.flatMap(_.regularMarketLastPrice)),
          open = qd.flatMap(_.openPrice),
          high = qd.flatMap(_.highPrice),
          low = qd.flatMap(_.lowPrice),
          close = qd.flatMap(_.closePrice),
          volume = qd.flatMap(_.totalVolume).orElse(ext.flatMap(_.totalVolume)),
          quoteTime = qd.flatMap(_.quoteTime).orElse(ext.flatMap(_.quoteTime)),
          bidSize = qd.flatMap(_.bidSize).orElse(ext.flatMap(_.bidSize)),
          askSize = qd.flatMap(_.askSize).orElse(ext.flatMap(_.askSize)),
          mark = qd.flatMap(_.mark).orElse(ext.flatMap(_.mark)),
          change = qd.flatMap(_.netChange).orElse(reg.flatMap(_.regularMarketNetChange)),
          percentChange = qd.flatMap(_.netPercentChange).orElse(reg.flatMap(_.regularMarketPercentChange)),
          fiftyTwoWeekHigh = qd.flatMap(_.`52WeekHigh`),
          fiftyTwoWeekLow = qd.flatMap(_.`52WeekLow`),
          openInterest = qd.flatMap(_.openInterest),
          volatility = qd.flatMap(_.volatility),
          moneyIntrinsicValue = qd.flatMap(_.moneyIntrinsicValue),
          timeValue = qd.flatMap(_.timeValue),
          delta = qd.flatMap(_.delta),
          gamma = qd.flatMap(_.gamma),
          theta = qd.flatMap(_.theta),
          vega = qd.flatMap(_.vega),
          rho = qd.flatMap(_.rho),
          theoreticalOptionValue = qd.flatMap(_.theoreticalOptionValue),
          underlyingPrice = qd.flatMap(_.underlyingPrice)
        )
      }
    } yield simplifiedQuotes
  }
  
  override def getQuote(
    symbol: String,
    fields: Option[String] = None
  ): Task[Quote] = {
    val queryParams = buildQueryParams("fields" -> fields)
    val endpoint = s"$baseEndpoint/$symbol/quotes$queryParams"
    
    val apiCall = client match {
      case seamless: SeamlessSchwabClient =>
        // Use the safe retry mechanism for seamless clients
        seamless.makeApiCallSafeWithRetry[Map[String, QuoteResponse]](endpoint)
      case _ =>
        // Fallback to safe standard approach for other clients
        for {
          token <- getValidToken()
          response <- client.makeApiCallSafe[Map[String, QuoteResponse]](endpoint, token.access_token)
        } yield response
    }
    
    for {
      apiResponse <- apiCall
      response <- apiResponse match {
        case SchwabApiResponse.Success(data) => ZIO.succeed(data)
        case SchwabApiResponse.Error(errors) =>
          val errorMessages = errors.map(_.message).mkString(", ")
          ZIO.fail(new RuntimeException(s"Schwab API error for symbol $symbol: $errorMessages"))
      }
      quoteResponse <- ZIO.fromOption(response.get(symbol))
        .orElseFail(new RuntimeException(s"Quote not found for symbol: $symbol"))
      // Convert QuoteResponse to simplified Quote
      qd = quoteResponse.quote
      ext = quoteResponse.extended
      reg = quoteResponse.regular
      
      quote = Quote(
        symbol = symbol,
        bid = qd.flatMap(_.bidPrice).orElse(ext.flatMap(_.bidPrice)),
        ask = qd.flatMap(_.askPrice).orElse(ext.flatMap(_.askPrice)),
        last = qd.flatMap(_.lastPrice).orElse(ext.flatMap(_.lastPrice)).orElse(reg.flatMap(_.regularMarketLastPrice)),
        open = qd.flatMap(_.openPrice),
        high = qd.flatMap(_.highPrice),
        low = qd.flatMap(_.lowPrice),
        close = qd.flatMap(_.closePrice),
        volume = qd.flatMap(_.totalVolume).orElse(ext.flatMap(_.totalVolume)),
        quoteTime = qd.flatMap(_.quoteTime).orElse(ext.flatMap(_.quoteTime)),
        bidSize = qd.flatMap(_.bidSize).orElse(ext.flatMap(_.bidSize)),
        askSize = qd.flatMap(_.askSize).orElse(ext.flatMap(_.askSize)),
        mark = qd.flatMap(_.mark).orElse(ext.flatMap(_.mark)),
        change = qd.flatMap(_.netChange).orElse(reg.flatMap(_.regularMarketNetChange)),
        percentChange = qd.flatMap(_.netPercentChange).orElse(reg.flatMap(_.regularMarketPercentChange)),
        fiftyTwoWeekHigh = qd.flatMap(_.`52WeekHigh`),
        fiftyTwoWeekLow = qd.flatMap(_.`52WeekLow`),
        openInterest = qd.flatMap(_.openInterest),
        volatility = qd.flatMap(_.volatility),
        moneyIntrinsicValue = qd.flatMap(_.moneyIntrinsicValue),
        timeValue = qd.flatMap(_.timeValue),
        delta = qd.flatMap(_.delta),
        gamma = qd.flatMap(_.gamma),
        theta = qd.flatMap(_.theta),
        vega = qd.flatMap(_.vega),
        rho = qd.flatMap(_.rho),
        theoreticalOptionValue = qd.flatMap(_.theoreticalOptionValue),
        underlyingPrice = qd.flatMap(_.underlyingPrice)
      )
    } yield quote
  }
  
  override def getOptionChain(
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
  ): Task[OptionChain] = {
    val queryParams = buildQueryParams(
      "symbol" -> Some(symbol),
      "contractType" -> contractType,
      "strikeCount" -> strikeCount,
      "includeQuotes" -> (if (includeQuotes) Some(true) else None),
      "strategy" -> strategy,
      "interval" -> interval,
      "strike" -> strike,
      "range" -> range,
      "fromDate" -> fromDate,
      "toDate" -> toDate,
      "volatility" -> volatility,
      "underlyingPrice" -> underlyingPrice,
      "interestRate" -> interestRate,
      "daysToExpiration" -> daysToExpiration,
      "expMonth" -> expMonth,
      "optionType" -> optionType
    )
    
    for {
      token <- getValidToken()
      response <- client.makeApiCall[OptionChain](
        s"$baseEndpoint/chains$queryParams",
        token.access_token
      )
    } yield response
  }
  
  override def getOptionExpirationChain(symbol: String): Task[OptionExpirationChain] = {
    val queryParams = buildQueryParams("symbol" -> Some(symbol))
    
    for {
      token <- getValidToken()
      response <- client.makeApiCall[OptionExpirationChain](
        s"$baseEndpoint/expirationchain$queryParams",
        token.access_token
      )
    } yield response
  }
  
  override def getPriceHistory(
    symbol: String,
    periodType: Option[String] = None,
    period: Option[Int] = None,
    frequencyType: Option[String] = None,
    frequency: Option[Int] = None,
    startDate: Option[Long] = None,
    endDate: Option[Long] = None,
    needExtendedHoursData: Boolean = true
  ): Task[PriceHistory] = {
    val queryParams = buildQueryParams(
      "symbol" -> Some(symbol),
      "periodType" -> periodType,
      "period" -> period,
      "frequencyType" -> frequencyType,
      "frequency" -> frequency,
      "startDate" -> startDate,
      "endDate" -> endDate,
      "needExtendedHoursData" -> Some(needExtendedHoursData)
    )
    
    for {
      token <- getValidToken()
      response <- client.makeApiCall[PriceHistory](
        s"$baseEndpoint/pricehistory$queryParams",
        token.access_token
      )
    } yield response
  }
  
  override def getMovers(
    index: String,
    sort: Option[String] = None,
    frequency: Option[Int] = None
  ): Task[List[Mover]] = {
    val queryParams = buildQueryParams(
      "sort" -> sort,
      "frequency" -> frequency
    )
    
    for {
      token <- getValidToken()
      response <- client.makeApiCall[MoverResponse](
        s"$baseEndpoint/movers/$index$queryParams",
        token.access_token
      )
    } yield response.screeners
  }
  
  override def getMarketHours(
    markets: List[String],
    date: Option[String] = None
  ): Task[MarketHours] = {
    // Validate inputs
    if (markets.isEmpty) {
      ZIO.fail(new IllegalArgumentException("Markets list cannot be empty"))
    } else {
      // Ensure markets are uppercase as required by Schwab API
      val validMarkets = Set("EQUITY", "OPTION", "BOND", "FUTURE", "FOREX")
      val upperMarkets = markets.map(_.toUpperCase)
      val invalidMarkets = upperMarkets.filterNot(validMarkets.contains)
      
      if (invalidMarkets.nonEmpty) {
        ZIO.logWarning(s"Invalid market types: ${invalidMarkets.mkString(", ")}. Valid types are: ${validMarkets.mkString(", ")}") *>
        ZIO.fail(new IllegalArgumentException(s"Invalid market types: ${invalidMarkets.mkString(", ")}"))
      } else {
        val marketsParam = upperMarkets.mkString(",")
        val queryParams = buildQueryParams(
          "markets" -> Some(marketsParam),
          "date" -> date
        )
        
        for {
          token <- getValidToken()
          response <- client.makeApiCall[MarketHours](
            s"$baseEndpoint/markets$queryParams",
            token.access_token
          )
        } yield response
      }
    }
  }
  
  override def getMarketHour(
    market: String,
    date: Option[String] = None
  ): Task[MarketHours] = {
    // The API doesn't support getting a single market, so we get all and filter
    getMarketHours(List(market.toLowerCase), date)
  }
  
  override def getInstruments(
    symbol: String,
    projection: String
  ): Task[List[InstrumentResponse]] = {
    val queryParams = buildQueryParams(
      "symbol" -> Some(symbol),
      "projection" -> Some(projection)
    )
    
    for {
      token <- getValidToken()
      response <- client.makeApiCall[InstrumentsResponse](
        s"$baseEndpoint/instruments$queryParams",
        token.access_token
      )
    } yield response.instruments
  }
  
  override def getInstrumentByCusip(cusip: String): Task[InstrumentResponse] = {
    for {
      token <- getValidToken()
      response <- client.makeApiCall[List[InstrumentResponse]](
        s"$baseEndpoint/instruments/$cusip",
        token.access_token
      )
      instrument <- ZIO.fromOption(response.headOption)
        .orElseFail(new RuntimeException(s"Instrument not found for cusip: $cusip"))
    } yield instrument
  }
}

object LiveMarketDataService {
  val layer: ZLayer[SchwabClient, Nothing, MarketDataService] =
    ZLayer.fromFunction(LiveMarketDataService(_))
}