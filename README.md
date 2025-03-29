# ZIO Schwab API

A Scala client for the Schwab API using ZIO. This library provides a type-safe, functional interface to the Schwab REST API endpoints.

## Features

- OAuth 2.0 flows (Authorization code, Client credentials, Refresh token)
- Integration with `MarketDataProvider` interface
- Fully asynchronous API using ZIO
- Configuration via file or environment variables
- Rate limiting
- Logging integration

## Requirements

- Scala 3.4.x
- ZIO 2.1.x
- ZIO-HTTP 3.0.0-RC8
- ZIO-JSON 0.7.0

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

# Optional settings (defaults shown)
# tokenUrl = "https://api.schwabapi.com/v1/oauth/token"
# redirectUri = "https://127.0.0.1"
# authUrl = "https://api.schwabapi.com/v1/oauth/authorize"
# apiBaseUrl = "https://api.schwabapi.com"
```

Alternatively, you can provide configuration via environment variables:

```
CLIENTID=YOUR_CLIENT_ID
CLIENTSECRET=YOUR_CLIENT_SECRET
```

### Basic Usage

```scala
import zio.*
import zio.http.Client
import com.github.zioschwabapi.client.*

object SchwabApiExample extends ZIOAppDefault {
  val program = for {
    // Get access token
    tokenResponse <- SchwabClient.getAccessToken
    _ <- Console.printLine(s"Access Token: ${tokenResponse.access_token}")

    // Make an API call
    apiResponse <- SchwabClient.makeRawApiCall("trader/v1/accounts", tokenResponse.access_token)
    _ <- Console.printLine(s"API Response: $apiResponse")
  } yield ()

  def run =
    program.provide(
      SchwabApiConfig.live,
      Client.default,
      SchwabClient.live
    )
}
```

### OAuth Flow

For the Authorization Code flow:

```scala
val program = for {
  // Get authorization URL
  authorizeUrl <- SchwabClient.authorize
  _ <- Console.printLine(s"Open this URL in your browser: $authorizeUrl")

  // Get the code from the user
  _ <- Console.printLine("Enter the authorization code:")
  code <- Console.readLine

  // Exchange code for token
  tokenResponse <- SchwabClient.getToken(code)
  
  // Use the token
  // ...
} yield ()
```


## Advanced Usage

## Error Handling

All methods return ZIO effects which can fail with appropriate errors. You can handle errors using ZIO's error handling mechanisms:

```scala
SchwabClient.makeApiCall[MyResponse]("some/endpoint", token)
  .tap(response => ZIO.log(s"Got response: $response"))
  .catchAll { error =>
    ZIO.log(s"Error: ${error.getMessage}") *>
    ZIO.fail(error)
  }
```

## License

This project is licensed under the MIT License - see the LICENSE file for details.