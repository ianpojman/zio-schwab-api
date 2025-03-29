package net.ipojman.schwab.zio

import zio.*
import zio.http.*
import zio.json.*
import zio.logging.*
import zio.logging.backend.SLF4J

/**
 * Example application demonstrating OAuth flow with Schwab API
 * This example demonstrates the "manual" approach without a running web server
 */
object ApiExample extends ZIOAppDefault {

  // Configure logging with SLF4J
  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  val program = for {
    // Step 1: Direct user to authorize URL
    authorizeUrl <- SchwabClient.authorize
    _            <- Console.printLine(s"[Schwab API] Open this URL in your browser to authenticate: $authorizeUrl")
    _            <- Console.printLine("[Schwab API] After authorizing, you'll be redirected to a page that might not load.")
    _            <- Console.printLine("[Schwab API] That's normal - just copy the full URL from your browser's address bar")
    _            <- Console.printLine("[Schwab API] and paste it below (it should contain '?code=...' in it):")

    // Step 2: User inputs the FULL CALLBACK URL (not just the code)
    callbackUrl  <- Console.readLine

    // Extract the code from the callback URL using our specialized extractor
    code         <- ZIO.succeed(AuthCodeExtractor.extractCode(callbackUrl))
    _            <- ZIO.logDebug(s"Extracted authorization code (truncated): ${code.take(5)}...")

    // Step 3: Get the initial access token using the authorization code
    tokenResponse <- SchwabClient.getToken(code)
    _             <- Console.printLine(s"[Schwab API] Successfully obtained access token!")
    _             <- ZIO.logDebug(s"Token details: expires in ${tokenResponse.expires_in.getOrElse("unknown")} seconds")

    // Step 4: Make an API call using the access token
    _             <- Console.printLine("[Schwab API] Making test API call to accounts endpoint...")
    apiResponse   <- SchwabClient.makeRawApiCall("trader/v1/accounts", tokenResponse.access_token)
    _             <- Console.printLine(s"[Schwab API] Response: $apiResponse")

    // Step 5: Refresh the access token when it expires (optional)
    _             <- ZIO.when(tokenResponse.refresh_token.isDefined) {
      for {
        _             <- Console.printLine("[Schwab API] Testing token refresh...")
        refreshToken  <- ZIO.fromOption(tokenResponse.refresh_token)
          .orElseFail(new RuntimeException("No refresh token available"))
        refreshedTokenResponse <- SchwabClient.refreshToken(refreshToken)
        _             <- Console.printLine(s"[Schwab API] Successfully refreshed token.")
        _             <- ZIO.logDebug(s"New token expires in ${refreshedTokenResponse.expires_in.getOrElse("unknown")} seconds")
      } yield ()
    }
  } yield ()

  def run =
    program.provide(
      SchwabApiConfig.live,
      Client.default,
      SchwabClient.live
    )
}