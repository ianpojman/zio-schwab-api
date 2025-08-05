# ZIO Schwab API

A comprehensive Scala client library for the Schwab Trading API using ZIO. This library provides type-safe, functional interfaces to Schwab's REST API endpoints for trading, market data, and account management.

## Features

### Core Features
- **OAuth 2.0 Authentication**: Multiple flows supported (Authorization Code, Client Credentials, Refresh Token)
- **Seamless Authentication**: Automatic OAuth flow with local HTTPS server and browser integration
- **Token Management**: Automatic token refresh with concurrent request synchronization
- **Type-Safe API**: Full Scala 3 support with comprehensive data models
- **ZIO Integration**: Fully asynchronous, composable effects using ZIO 2.x
- **SSL Certificate Management**: Automatic local HTTPS setup with self-signed certificates
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
- **Order Placement**: Submit market, limit, and stop orders (requires additional Schwab approval)
- **Order Modification**: Replace and cancel existing orders
- **Order Tracking**: Real-time order status and execution details

### Enhanced Order Service
- **Symbol-Specific Queries**: Efficiently query orders for specific symbols
- **Smart Caching**: Automatic caching of order data to reduce API calls
- **Cache Management**: Configurable cache TTL and invalidation methods
- **Simplified Order Model**: Easy-to-use order data structures

### Advanced Features
- **Multi-Account Support**: Handle multiple trading accounts
- **Error Handling**: Comprehensive error handling with ZIO error channels
- **Rate Limiting**: Built-in API rate limiting compliance
- **Logging Integration**: Detailed logging for debugging and monitoring
- **Windows Compatibility**: Specialized Windows SSL configuration support
- **Retry Logic**: Automatic retry on 401 errors with token refresh

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
import java.time.LocalDate

object QuickExample extends ZIOAppDefault {
  val program = for {
    // Get real-time quotes
    quotes <- MarketDataService.getQuotes(List("AAPL", "MSFT", "GOOGL"))
    _ <- ZIO.foreach(quotes.toList) { case (symbol, quote) =>
      Console.printLine(s"$symbol: $$${quote.last.getOrElse("N/A")}")
    }
    
    // Get historical orders using enhanced service
    orderService <- ZIO.service[SchwabOrderServiceEnhanced]
    orders <- orderService.getOrdersForSymbol("AAPL", LocalDate.now.minusDays(30), LocalDate.now)
    _ <- Console.printLine(s"Found ${orders.size} AAPL orders")
  } yield ()

  def run = program.provide(
    LiveMarketDataService.layer,
    SchwabOrderServiceEnhanced.layer,
    SeamlessSchwabClient.live  // Handles OAuth automatically
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
  // 5. Automatically refresh tokens when they expire
  
  // Your API calls will work immediately after this setup
  quotes <- MarketDataService.getQuotes(List("AAPL"))
  _ <- Console.printLine(s"AAPL: $$${quotes("AAPL").last.getOrElse("N/A")}")
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

### Enhanced Order Service

The enhanced order service provides efficient order querying with built-in caching:

```scala
import java.time.LocalDate

// Get orders for a specific symbol
val orderService = ZIO.service[SchwabOrderServiceEnhanced]
orderService.flatMap { service =>
  service.getOrdersForSymbol("AAPL", LocalDate.now.minusDays(30), LocalDate.now)
}

// Get only filled orders for a symbol
orderService.flatMap { service =>
  service.getFilledOrdersForSymbol("MSFT", LocalDate.now.minusDays(7), LocalDate.now)
}

// Get all orders across all symbols (uses caching)
orderService.flatMap { service =>
  service.getOrdersForAllSymbols(LocalDate.now.minusDays(30), LocalDate.now)
}

// Cache management
orderService.flatMap { service =>
  // Invalidate entire cache
  service.invalidateCache()
  
  // Invalidate specific date range
  service.invalidateCacheForDateRange(LocalDate.now.minusDays(7), LocalDate.now)
}
```

### Original Order Service

For direct API access without caching:

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

// Place new order (requires additional Schwab approval)
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
// Using enhanced order service for better performance
val analysis = for {
  service <- ZIO.service[SchwabOrderServiceEnhanced]
  
  // Get all AAPL orders (cached after first call)
  aaplOrders <- service.getFilledOrdersForSymbol("AAPL", LocalDate.now.minusDays(30), LocalDate.now)
  
  // Separate buy/sell orders
  buyOrders = aaplOrders.filter(_.instruction == "BUY")
  sellOrders = aaplOrders.filter(_.instruction == "SELL")
  
  // Calculate positions
  totalBought = buyOrders.map(_.quantity).sum
  totalSold = sellOrders.map(_.quantity).sum
  netPosition = totalBought - totalSold
  
  // Calculate average prices
  avgBuyPrice = if (buyOrders.nonEmpty) 
    buyOrders.flatMap(_.price).sum / buyOrders.length 
  else 0.0
  
  avgSellPrice = if (sellOrders.nonEmpty)
    sellOrders.flatMap(_.price).sum / sellOrders.length
  else 0.0
  
  _ <- Console.printLine(s"Net position: $netPosition shares")
  _ <- Console.printLine(f"Avg buy price: $$${avgBuyPrice}%.2f")
  _ <- Console.printLine(f"Avg sell price: $$${avgSellPrice}%.2f")
} yield ()
```

## Examples

### Run the Complete Examples

```bash
# Market data example
sbt "runMain net.ipojman.schwab.zio.MarketDataExample"

# API server example
sbt "runMain net.ipojman.schwab.zio.ApiExampleWithServer"

# Client credentials OAuth flow
sbt "runMain net.ipojman.schwab.zio.ClientCredentialsExample"
```

The examples demonstrate:
- Real-time market data retrieval
- Historical order analysis with caching
- Trade performance metrics
- Position calculations
- Order status distribution
- OAuth authentication flows

### Build and Development

```bash
# Compile project
sbt compile

# Run tests
sbt test

# Run specific test
sbt "testOnly *SpecificTestClass"

# Launch SBT interactive shell
sbt

# Clean and rebuild
sbt clean compile

# Publish to local repository
sbt publishLocal
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

The seamless client automatically handles:
- Token expiration and refresh
- 401 authentication errors
- Concurrent request synchronization
- SSL certificate issues

## Caching Configuration

The enhanced order service includes intelligent caching:

- **Default TTL**: 5 minutes (configurable)
- **Cache Key**: Date range (fromDate, toDate)
- **Automatic Invalidation**: On TTL expiration
- **Manual Invalidation**: Via provided methods

Benefits:
- Reduces API calls when querying multiple symbols
- Improves response times for repeated queries
- Prevents hitting API rate limits

## Windows Support

The library includes specialized Windows SSL configuration. If you encounter certificate issues on Windows:

1. The library automatically creates self-signed certificates
2. Accept the browser security warning on first use
3. For persistent issues, see `WINDOWS_AUTH_FIX.md`

## Architecture

- **SchwabClient**: Main API client with `LiveSchwabClient` and `SeamlessSchwabClient` implementations
- **Service Layers**: Domain-specific services (`MarketDataService`, `OrderService`, `SchwabOrderServiceEnhanced`)
- **Models**: Type-safe data models with JSON codecs using zio-json
- **OAuth**: Multiple authentication flows with automatic token management
- **SSL**: Certificate management for local HTTPS servers
- **Caching**: Smart caching layer for order data
- **Synchronization**: Thread-safe token refresh and OAuth flow management

## Recent Updates

- **Token Refresh Synchronization**: Fixed cascading OAuth attempts on token expiry
- **Order Service Caching**: Added intelligent caching to reduce API calls
- **Enhanced Order Service**: New service with symbol-specific queries and caching
- **SSL Certificate Caching**: Improved SSL setup performance
- **Concurrent Request Handling**: Better handling of multiple simultaneous API calls

## License

This project is licensed under the MIT License - see the LICENSE file for details.