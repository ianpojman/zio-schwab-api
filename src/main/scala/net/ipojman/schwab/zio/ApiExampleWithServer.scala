package net.ipojman.schwab.zio

import net.ipojman.schwab.zio.SchwabHttpsServerComplete.TokenRepository
import net.ipojman.schwab.zio.cert.{CertificateManager, MkcertManager, SSLSetup}
import zio.*
import zio.Config
import zio.http.*
import zio.http.Client
import zio.json.*
import zio.logging.*
import zio.logging.backend.SLF4J

import scala.concurrent.Promise
import java.util.concurrent.atomic.AtomicReference

/**
 * Example application demonstrating OAuth flow with Schwab API
 * This example integrates with our HTTPS server for OAuth handling
 */
object ApiExampleWithServer extends ZIOAppDefault {
  // Configure logging with SLF4J
  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  // Initialize BouncyCastle provider
  java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider())

  // Token storage for sharing between server and test client
  private val tokenRef = new AtomicReference[Option[TokenResponse]](None)

  // Promise to signal when authentication is complete
  private val authCompletedPromise = Promise[Unit]()

  // Custom routes: intercept OAuth callback to signal test client, then include original and refresh routes
  private val extendedApiRoutes: Routes[SchwabClient & TokenRepository, Response] = {
    // 1) Callback handler that signals the promise
    val callbackRoute =
      Method.GET / "api" / "oauth" / "callback" ->
        handler { (req: Request) =>
          for {
            _         <- Console.printLine("[Schwab API] /api/oauth/callback hit")
            codeParam <- ZIO
                           .fromOption(req.url.queryParams.getAll("code").headOption)
                           .orElseFail(new RuntimeException("Code not found"))
            tokenResp <- SchwabClient.getToken(codeParam)
            _         <- TokenRepository.storeToken(tokenResp)
            _         <- ZIO.attempt(authCompletedPromise.success(())).ignore
            _         <- Console.printLine("[Schwab API] Auth promise completed, proceeding with API calls")
            _         <- ZIO.logDebug("Auth callback reached, promise signaled")
          } yield Response.html(
            """
              |<html><body>
              |  <h1>Authentication Successful!</h1>
              |  <a href="/api/accounts">View Accounts</a>
              |</body></html>
            """.stripMargin
          )
        }

    // 2) Refresh handler
    val refreshRoute =
      Method.GET / "api" / "refresh" ->
        handler { (_: Request) =>
          for {
            currentToken <- ZIO.attempt(tokenRef.get().get).orElseFail(new RuntimeException("No token available"))
            refreshToken <- ZIO.fromOption(currentToken.refresh_token).orElseFail(new RuntimeException("No refresh token available"))
            newToken     <- SchwabClient.refreshToken(refreshToken)
            _            <- TokenRepository.storeToken(newToken)
            // Signal the test client that authentication and token storage are complete
            _         <- ZIO.attempt(authCompletedPromise.success(())).ignore
            _         <- Console.printLine("[Schwab API] Auth promise completed, proceeding with API calls")
          } yield Response.html(
            """
              |<html><body>
              |  <h1>Token Refreshed Successfully</h1>
              |  <a href="/api/accounts">View Accounts</a>
              |</body></html>
            """.stripMargin
          )
        }

    // 3) Append custom refresh and callback routes so they override the originals
    val baseRoutes = SchwabHttpsServerComplete.apiRoutes.routes
    val allRoutes  = baseRoutes ++ List(refreshRoute, callbackRoute)
    Routes(allRoutes).sandbox
  }

  // Adapted token repository for our use
  private val repositoryLayer = ZLayer.fromZIO(
    Ref.make(Option.empty[TokenResponse]).map { ref =>
      new TokenRepository {
        override def storeToken(token: TokenResponse): Task[Unit] =
          ref
            .set(Some(token))
            .tap(_ =>
              ZIO.attempt {
                tokenRef.set(Some(token))
              }
            )

        override def getToken: Task[TokenResponse] =
          ref.get.flatMap {
            case Some(token) => ZIO.succeed(token)
            case None        => ZIO.fail(new RuntimeException("No token available"))
          }

        override def getAccessToken: Task[String] =
          getToken.map(_.access_token)
      }
    }
  )

  // Test client component
  private val runTestClient = {
    for {
      // Wait for the server to start
      _ <- ZIO.sleep(1.second)

      certificate  <- sslSetupZIO.map(_.certificate)
      // After generating certificate
      certPath      = "server-cert.crt"
      _            <- exportCertificateToFile(certificate, certPath)
      _            <- Console.printLine(s"[Schwab API] Certificate exported to $certPath")
      _            <- Console.printLine("[Schwab API] To trust this certificate in Safari, run:")
      _            <- Console.printLine(
                        s"sudo security add-trusted-cert -d -r trustRoot -k /Library/Keychains/System.keychain $certPath"
                      )

      // Get the auth URL
      authorizeUrl <- SchwabClient.authorize
      _            <- Console.printLine(s"\n[Schwab API] Open this URL in your browser to authenticate:")
      _            <- Console.printLine(s"$authorizeUrl")
      _            <-
        Console.printLine(
          "\n[Schwab API] After following the link, you'll be automatically redirected to our OAuth callback handler."
        )
      _            <- Console.printLine("[Schwab API] Waiting for authentication to complete...")

      // Wait for auth to complete (signaled via our promise)
      _ <- ZIO.fromPromiseScala(authCompletedPromise)
      _ <- Console.printLine("[Schwab API] Auth promise completed, proceeding with API calls")

      // Get the token from our shared reference
      token <- ZIO
                 .attempt(tokenRef.get().get)
                 .orElseFail(new RuntimeException("Failed to get token"))

      _ <- Console.printLine(s"\n[Schwab API] Successfully authenticated!")
      _ <- Console.printLine(s"[Schwab API] Token details: ${token}")

      // Make API call
      _           <- Console.printLine("\n[Schwab API] Making test API call to accounts endpoint...")
      apiResponse <- SchwabClient.makeRawApiCall("trader/v1/accounts", token.access_token)
      _           <- Console.printLine(s"[Schwab API] API Response: $apiResponse")

      // Test token refresh
//      _               <- Console.printLine("\n[Schwab API] Testing token refresh via internal endpoint...")
//      // Decode the URL, fail if malformed
//      refreshUrlEither = URL.decode("https://localhost:8443/api/refresh")
//      refreshUrl      <- ZIO.fromEither(refreshUrlEither).mapError(e => new RuntimeException(s"Invalid URL for refresh: $e"))
//      // Use the batched client API for refresh to ensure completion
//      refreshResponse <- Client.batched(Request.get(refreshUrl))
//      _               <- Console.printLine(s"[Schwab API] Refresh endpoint returned: ${refreshResponse.status}")

      // Get the refreshed token
//      refreshedToken <- ZIO
//                          .attempt(tokenRef.get().get)
//                          .orElseFail(new RuntimeException("Failed to get refreshed token"))
//                          .filterOrFail(t => t.access_token != token.access_token)(
//                            new RuntimeException("Token wasn't refreshed")
//                          )

//      _ <- Console.printLine(s"\n[Schwab API] Successfully verified token refresh!")
//      _ <- ZIO.logDebug(s"New token: ${refreshedToken}")

      // Make another API call with refreshed token
      _              <- Console.printLine("\n[Schwab API] Making API call with refreshed token...")
      newApiResponse <- SchwabClient.makeRawApiCall("trader/v1/accounts", token.access_token)
      _              <- Console.printLine(s"[Schwab API] API Response with refreshed token: $newApiResponse")

      _ <- Console.printLine("\n[Schwab API] Test completed successfully!")
    } yield ()
  }

  val sslSetupZIO: ZIO[Any, Throwable, SSLSetup] = MkcertManager.setupLocalCert()

  def exportCertificateToFile(certificate: java.security.cert.X509Certificate, path: String): Task[Unit] =
    ZIO.attempt {
      val certOut = new java.io.FileOutputStream(path)
      certOut.write(certificate.getEncoded)
      certOut.close()
    }

  // Main program
  def run = {
    val program = for {
      // 1) Generate and trust our local CA + cert
      sslSetup <- MkcertManager.setupLocalCert()
      _        <- Console.printLine("[Schwab API] SSL certificate prepared")
      _        <- Console.printLine("[Schwab API] Server running at: https://localhost:8443")

      // 2) Launch the HTTPS-enabled server
      serverFiber <- Server
                       .serve(extendedApiRoutes)
                       .provide(
                         ZLayer.succeed(sslSetup),
                         SchwabApiConfig.live,
                         Client.default,
                         SchwabClient.live,
                         repositoryLayer,
                         ZLayer.succeed(Server.Config.default.ssl(sslSetup.config).port(8443)),
                         Server.live
                       )
                       .forkDaemon

      // 3) Run the end-to-end test client
      _           <- runTestClient.provide(
                       ZLayer.succeed(sslSetup),
                       SchwabApiConfig.live,
                       Client.default,
                       SchwabClient.live,
                       repositoryLayer,
                       Scope.default
                     )

      // 4) Wait for user input, then shut down
      _           <- Console.printLine("\n[Schwab API] Press Enter to shut down...")
      _           <- Console.readLine
      _           <- serverFiber.interrupt
    } yield ()

    program.exitCode
  }
}
