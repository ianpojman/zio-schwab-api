# ZIO Schwab API

A comprehensive Scala client library for the Schwab Trading API using ZIO. This library provides type-safe, functional interfaces to Schwab's REST API endpoints for trading, market data, and account management.

## Features

### Core Features
- **OAuth 2.0 Authentication**: Multiple flows supported (Authorization Code, Client Credentials, Refresh Token)
- **Seamless Authentication**: Automatic OAuth flow with local HTTPS server and browser integration
- **Type-Safe API**: Full Scala 3 support with comprehensive data models
- **ZIO Integration**: Fully asynchronous, composable effects using ZIO 2.x
- **SSL Certificate Management**: Automatic local HTTPS setup using mkcert
- **Configuration Management**: File-based or environment variable configuration

### Market Data Services
- **Real-time Quotes**: Single and batch quote retrieval for stocks, ETFs, and options
- **Option Chains**: Complete option chain data with Greeks and volatility
- **Price History**: Historical candlestick data with configurable periods and frequencies
- **Market Movers**: Top gainers/losers for major indices
- **Market Hours**: Trading hours for equity and option markets
- **Instrument Search**: Symbol lookup and instrument details

### Order Management
- **Historical Orders**: Query orders with date ranges, status filters, and pagination
- **Order Placement**: Submit market, limit, and stop orders
- **Order Modification**: Replace and cancel existing orders
- **Order Tracking**: Real-time order status and execution details
- **Trade Performance Analysis**: Built-in tools for analyzing historical trades
- **Position Calculations**: Automatic buy/sell tracking and net position calculations

### Advanced Features
- **Multi-Account Support**: Handle multiple trading accounts
- **Error Handling**: Comprehensive error handling with ZIO error channels
- **Rate Limiting**: Built-in API rate limiting compliance
- **Logging Integration**: Detailed logging for debugging and monitoring
- **Windows Compatibility**: Specialized Windows SSL configuration support

## Requirements

- Scala 3.6.4
- ZIO 2.1.16
- ZIO-HTTP 3.0.11
- ZIO-JSON 0.7.3
- Java 21+

## Getting Started

### Add dependency

Note: this is not yet published anywhere, so you will need to clone the repository and publish it locally.

```sbt
libraryDependencies += "com.github.zio-schwab-api" %% "zio-schwab-api" % "0.0.1-SNAPSHOT"
```

### Configuration

Create a configuration file at `~/.schwab.conf`:

```hocon
clientId = "YOUR_CLIENT_ID"
clientSecret = "YOUR_CLIENT_SECRET"
redirectUri = "https://127.0.0.1:8443/api/oauth/callback"

# Optional settings (defaults shown)
# tokenUrl = "https://api.schwabapi.com/v1/oauth/token"
# authUrl = "https://api.schwabapi.com/v1/oauth/authorize"
# apiBaseUrl = "https://api.schwabapi.com"
```

Alternatively, you can provide configuration via environment variables:

```bash
export CLIENTID="YOUR_CLIENT_ID"
export CLIENTSECRET="YOUR_CLIENT_SECRET"
export REDIRECTURI="https://127.0.0.1:8443/api/oauth/callback"
```

### Quick Start with Seamless Client

The recommended approach uses `SeamlessSchwabClient` which handles OAuth authentication automatically:

```scala
import zio.*
import net.ipojman.schwab.zio.*

object QuickExample extends ZIOAppDefault {
  val program = for {
    // Get real-time quotes
    quotes <- MarketDataService.getQuotes(List("AAPL", "MSFT", "GOOGL"))
    _ <- ZIO.foreach(quotes.toList) { case (symbol, quote) =>
      Console.printLine(s"$symbol: ${quote.last.getOrElse("N/A")}")
    }
    
    // Get historical orders
    orders <- OrderService.getOrders(
      fromEnteredTime = ZonedDateTime.now().minusDays(7),
      toEnteredTime = ZonedDateTime.now(),
      maxResults = Some(10)
    )
    _ <- Console.printLine(s"Found ${orders.size} orders")
  } yield ()

  def run = program.provide(
    LiveMarketDataService.layer,
    LiveOrderService.layer,
    SchwabClient.seamless  // Handles OAuth automatically
  )
}
```

### Manual OAuth Flow

For more control over the authentication process:

```scala
val program = for {
  // The seamless client will:
  // 1. Start a local HTTPS server on port 8443
  // 2. Open your browser to Schwab's OAuth page
  // 3. Handle the callback automatically
  // 4. Extract and store the access token
  
  // Your API calls will work immediately after this setup
  quotes <- MarketDataService.getQuotes(List("AAPL"))
  _ <- Console.printLine(s"AAPL: ${quotes("AAPL").last}")
} yield ()
```


## API Reference

### Market Data Service

```scala
// Get real-time quotes
MarketDataService.getQuotes(List("AAPL", "MSFT"))
MarketDataService.getQuote("SPY", Some("quote,fundamental"))

// Get option chains
MarketDataService.getOptionChain(
  symbol = "AAPL",
  contractType = Some("ALL"),
  strikeCount = Some(5),
  includeQuotes = true
)

// Get price history
MarketDataService.getPriceHistory(
  symbol = "TSLA",
  periodType = Some("day"),
  period = Some(5),
  frequencyType = Some("minute"),
  frequency = Some(5)
)

// Get market movers
MarketDataService.getMovers("$SPX", Some("PERCENT_CHANGE_UP"))

// Get market hours
MarketDataService.getMarketHours(List("EQUITY", "OPTION"))

// Search instruments
MarketDataService.getInstruments("AAPL", "symbol-search")
```

### Order Service

```scala
// Get historical orders with filters
OrderService.getOrders(
  fromEnteredTime = ZonedDateTime.now().minusDays(30),
  toEnteredTime = ZonedDateTime.now(),
  maxResults = Some(100),
  status = Some(OrderStatus.Filled)
)

// Get orders for specific account
OrderService.getOrdersByAccount(
  accountNumber = "12345678",
  fromEnteredTime = ZonedDateTime.now().minusDays(7),
  toEnteredTime = ZonedDateTime.now()
)

// Get specific order
OrderService.getOrder("12345678", 1003811730601L)

// Place new order
val orderRequest = OrderRequest(
  session = "NORMAL",
  duration = "DAY",
  orderType = "LIMIT",
  quantity = 100,
  price = Some(150.00),
  orderLegCollection = List(
    OrderLegRequest(
      orderLegType = "EQUITY",
      instrument = OrderInstrumentRequest("AAPL", "EQUITY"),
      instruction = "BUY",
      quantity = 100
    )
  )
)
OrderService.placeOrder("12345678", orderRequest)

// Cancel order
OrderService.cancelOrder("12345678", 1003811730601L)
```

### Trade Performance Analysis

The library includes built-in utilities for analyzing historical trades:

```scala
// Get all historical orders
val orders = OrderService.getOrders(
  fromEnteredTime = ZonedDateTime.now().minusDays(30),
  toEnteredTime = ZonedDateTime.now()
)

// Filter by instrument
val aaplOrders = orders.filter(order => 
  order.orderLegCollection.exists(_.instrument.symbol == "AAPL")
)

// Separate buy/sell orders
val buyOrders = aaplOrders.filter(order =>
  order.orderLegCollection.exists(leg => 
    leg.instruction.contains("BUY") && order.status == OrderStatus.Filled
  )
)

// Calculate positions
val totalBought = buyOrders.map(_.filledQuantity).sum
val averagePrice = buyOrders.flatMap(_.orderActivityCollection.flatten)
  .flatMap(_.executionLegs.flatten)
  .map(_.price).sum / buyOrders.size
```

## Examples

### Run the Complete Example

```bash
sbt "runMain net.ipojman.schwab.zio.MarketDataExample"
```

This example demonstrates:
- Real-time market data retrieval
- Historical order analysis
- Trade performance metrics
- Position calculations
- Order status distribution

### Build and Development

```bash
# Compile project
sbt compile

# Run tests
sbt test

# Run specific example
sbt "runMain net.ipojman.schwab.zio.MarketDataExample"
sbt "runMain net.ipojman.schwab.zio.ClientCredentialsExample"

# Clean and rebuild
sbt clean compile
```

## Error Handling

All methods return ZIO effects with comprehensive error handling:

```scala
MarketDataService.getQuotes(List("AAPL"))
  .tap(quotes => ZIO.log(s"Got quotes: $quotes"))
  .catchAll { error =>
    ZIO.logError(s"Failed to get quotes: ${error.getMessage}") *>
    ZIO.succeed(Map.empty) // Fallback to empty results
  }
```

## Windows Support

The library includes specialized Windows SSL configuration. If you encounter certificate issues on Windows, see `WINDOWS_AUTH_FIX.md` for detailed setup instructions.

## Architecture

- **SchwabClient**: Main API client with `LiveSchwabClient` and `SeamlessSchwabClient` implementations
- **Service Layers**: Domain-specific services (`MarketDataService`, `OrderService`)
- **Models**: Type-safe data models with JSON codecs using zio-json
- **OAuth**: Multiple authentication flows with automatic token management
- **SSL**: Certificate management for local HTTPS servers

## License

This project is licensed under the MIT License - see the LICENSE file for details.