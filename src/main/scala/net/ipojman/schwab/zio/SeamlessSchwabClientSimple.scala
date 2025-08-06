package net.ipojman.schwab.zio

import zio.*
import zio.json.*
import zio.http.*
import java.awt.Desktop
import java.net.URI
import scala.concurrent.duration.*
import java.io.{File, PrintWriter}
import java.nio.file.{Files, Paths}
import net.ipojman.schwab.zio.models.{SchwabApiResponse, SchwabErrorResponse}

/**
 * Alternative implementation with simpler server readiness approach
 * This version uses a fixed delay but with better error handling
 */
class SeamlessSchwabClientSimple(
  underlying: SchwabClient,
  config: SchwabApiConfig,
  tokenStorage: TokenStorage
) extends SchwabClient {
  
  // Delegate most methods to underlying client
  def authorize: UIO[String] = underlying.authorize
  def refreshToken(refreshToken: String): Task[TokenResponse] = underlying.refreshToken(refreshToken)
  def getAccessToken: Task[TokenResponse] = underlying.getAccessToken
  def makeApiCall[T: JsonDecoder](endpoint: String, accessToken: String): Task[T] = 
    underlying.makeApiCall(endpoint, accessToken)
  def makeApiCallSafe[T: JsonDecoder](endpoint: String, accessToken: String): Task[SchwabApiResponse[T]] =
    underlying.makeApiCallSafe(endpoint, accessToken)
  def makeRawApiCall(endpoint: String, accessToken: String): Task[String] = 
    underlying.makeRawApiCall(endpoint, accessToken)
  def getUserPreference(accessToken: String): Task[String] = 
    underlying.getUserPreference(accessToken)
  
  // Enhanced getToken that handles OAuth flow automatically
  def getToken(code: String): Task[TokenResponse] = underlying.getToken(code)
  
  /**
   * Get a valid access token, handling OAuth flow automatically if needed
   */
  def ensureAuthenticated(): Task[TokenResponse] = {
    tokenStorage.getToken().flatMap {
      case Some(token) if isTokenValid(token) => 
        ZIO.succeed(token)
      case Some(token) if token.refresh_token.isDefined =>
        refreshAndStoreToken(token.refresh_token.get)
      case _ => 
        initiateOAuthFlow()
    }
  }
  
  private def isTokenValid(token: TokenResponse): Boolean = {
    // Simple check - in production you'd check expiry time
    token.access_token.nonEmpty
  }
  
  private def refreshAndStoreToken(refreshToken: String): Task[TokenResponse] = {
    for {
      newToken <- underlying.refreshToken(refreshToken)
      _ <- tokenStorage.storeToken(newToken)
    } yield newToken
  }
  
  private def initiateOAuthFlow(): Task[TokenResponse] = {
    for {
      _ <- ZIO.logInfo("Starting OAuth authentication flow...")
      
      // Start embedded HTTP server for OAuth callback
      authCompleted <- Promise.make[Throwable, TokenResponse]
      serverFiber <- startAuthServer(authCompleted).fork
      
      // Wait for server to be ready - Windows needs more time
      delay = if (java.lang.System.getProperty("os.name").toLowerCase.contains("windows")) zio.Duration.fromSeconds(3) else zio.Duration.fromSeconds(1)
      _ <- ZIO.logInfo(s"Waiting ${delay.toSeconds} seconds for server to start...")
      _ <- ZIO.sleep(delay)
      
      // Open browser to Schwab auth page
      authUrl = s"${config.authUrl}?client_id=${config.clientId}&redirect_uri=${config.redirectUri}"
      _ <- ZIO.logInfo(s"Opening browser for authentication...")
      _ <- BrowserLauncher.openBrowser(authUrl)
        .catchAll { err =>
          ZIO.logError(s"Failed to open browser: ${err.getMessage}") *>
          ZIO.logInfo(s"Please manually open this URL in your browser: $authUrl") *>
          ZIO.unit
        }
      
      // Wait for authentication to complete (with timeout)
      token <- authCompleted.await
        .timeoutFail(new Exception("OAuth authentication timeout (5 minutes)"))(5.minutes)
        .ensuring(serverFiber.interrupt)
      
      _ <- tokenStorage.storeToken(token)
      _ <- ZIO.logInfo("OAuth authentication completed successfully")
    } yield token
  }
  
  private def startAuthServer(authCompleted: Promise[Throwable, TokenResponse]): Task[Unit] = {
    val callbackPath = new URI(config.redirectUri).getPath
    
    // Build the route path dynamically from the callback URL
    val pathSegments = callbackPath.split("/").filter(_.nonEmpty).toList
    val authRoute = pathSegments.foldLeft(Method.GET / Root)((route, segment) => route / segment) ->
      handler { (req: Request) =>
        for {
          code <- ZIO.fromOption(req.url.queryParams.getAll("code").headOption)
            .orElseFail(new RuntimeException("Authorization code not found"))
          
          _ <- ZIO.logInfo(s"Received authorization code")
          token <- underlying.getToken(code)
          _ <- authCompleted.succeed(token)
          
        } yield Response.html(
          """<html>
            |<head>
            |  <title>Authentication Successful</title>
            |  <style>
            |    body { font-family: Arial, sans-serif; text-align: center; padding: 50px; }
            |    h1 { color: #4CAF50; }
            |  </style>
            |</head>
            |<body>
            |  <h1>Authentication Successful!</h1>
            |  <p>You can now close this window and return to your application.</p>
            |  <script>
            |    setTimeout(function() { window.close(); }, 2000);
            |  </script>
            |</body>
            |</html>""".stripMargin
        )
      }
    
    val uri = new URI(config.redirectUri)
    val isHttps = uri.getScheme == "https"
    val port = uri.getPort match {
      case -1 => 
        // Extract port from URL if present (e.g., http://localhost:8080/)
        if (uri.getAuthority != null && uri.getAuthority.contains(":")) {
          uri.getAuthority.split(":").last.toInt
        } else if (isHttps) {
          443
        } else {
          80
        }
      case p => p
    }
    
    for {
      _ <- ZIO.logInfo(s"Starting OAuth callback server on port $port for path: $callbackPath (HTTPS: $isHttps)")
      _ <- ZIO.logInfo("Note: The browser will open in a few seconds...")
      
      serverConfig <- if (isHttps) {
        // Create self-signed certificate for HTTPS on Windows
        import cert.WindowsSSLSetup
        for {
          sslSetup <- WindowsSSLSetup.createSelfSignedCertificate("127.0.0.1")
          _ <- ZIO.logInfo("Created self-signed certificate for HTTPS")
        } yield Server.Config.default.port(port).ssl(sslSetup.config)
      } else {
        ZIO.succeed(Server.Config.default.port(port))
      }
      
      _ <- Server.serve(Routes(authRoute).sandbox)
        .provide(
          ZLayer.succeed(serverConfig),
          Server.live
        )
        .tapError { err =>
          ZIO.logError(s"Server error: ${err.getMessage}") *>
          ZIO.logInfo(s"Common causes on Windows:") *>
          ZIO.logInfo(s"- Port $port already in use") *>
          ZIO.logInfo(s"- Windows Firewall blocking the port") *>
          ZIO.logInfo(s"- Need to run as Administrator")
        }
    } yield ()
  }
}

object SeamlessSchwabClientSimple {
  
  /**
   * Create a seamless client layer that handles OAuth automatically
   */
  val layer: ZLayer[SchwabApiConfig & Client, Nothing, SchwabClient] = 
    ZLayer {
      for {
        config <- ZIO.service[SchwabApiConfig]
        client <- ZIO.service[Client]
        underlying = new LiveSchwabClient(config, client)
        tokenStorage = new FileTokenStorage()
      } yield new SeamlessSchwabClientSimple(underlying, config, tokenStorage)
    }
  
  /**
   * Live layer with seamless OAuth - this is what apps should use
   */
  val live: ZLayer[Any, Throwable, SchwabClient] = {
    val configAndClient = SchwabApiConfig.live ++ Client.default
    configAndClient >>> layer
  }
}