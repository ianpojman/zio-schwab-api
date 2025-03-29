package net.ipojman.schwab.zio

import zio.*
import zio.http.*
import zio.json.*
import zio.logging.*
import zio.logging.backend.SLF4J

/**
 * Example application demonstrating client credentials flow with Schwab API
 */
object ClientCredentialsExample extends ZIOAppDefault {

  // Configure logging with SLF4J
  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  val program = for {
    // Get the access token using client credentials
    _            <- Console.printLine("[Schwab API] Getting access token via client credentials...")
    tokenResponse <- SchwabClient.getAccessToken
    _            <- Console.printLine(s"[Schwab API] Access Token: ${tokenResponse.access_token.take(10)}... (truncated)")
    _            <- ZIO.logDebug(s"Full access token: ${tokenResponse.access_token}")
    _            <- ZIO.logDebug(s"Token expires in: ${tokenResponse.expires_in.getOrElse("unknown")} seconds")

    // Make an API call using the access token
    _            <- Console.printLine("[Schwab API] Making API call to accounts endpoint...")
    apiResponse  <- SchwabClient.makeRawApiCall("trader/v1/accounts", tokenResponse.access_token)
      .catchAll { e =>
        Console.printLine(s"[Schwab API] Error: ${e.getMessage}") *>
          ZIO.succeed("Error occurred")
      }
    _            <- Console.printLine(s"[Schwab API] Response: $apiResponse")
  } yield ()

  def run =
    program.provide(
      SchwabApiConfig.live,
      Client.default,
      SchwabClient.live
    )
}
