package net.ipojman.schwab.zio

import zio.*
import zio.json.*
import zio.http.*
import java.awt.Desktop
import java.net.URI
import scala.concurrent.duration.*
import java.io.{File, PrintWriter}
import java.nio.file.{Files, Paths}

/**
 * A Schwab client that handles OAuth authentication seamlessly
 * Opens browser automatically and stores tokens for future use
 */
class SeamlessSchwabClient(
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
  def makeRawApiCall(endpoint: String, accessToken: String): Task[String] = 
    underlying.makeRawApiCall(endpoint, accessToken)
  
  /**
   * Make an API call with automatic retry on 401 errors
   * This will attempt to refresh the token and retry the call once
   */
  def makeApiCallWithRetry[T: JsonDecoder](endpoint: String): Task[T] = {
    for {
      token <- ensureAuthenticated()
      result <- makeApiCall[T](endpoint, token.access_token).catchSome {
        case e: RuntimeException if e.getMessage.contains("401") =>
          ZIO.logInfo(s"Received 401 error for $endpoint, forcing token refresh...") *>
          ensureAuthenticated(forceRefresh = true).flatMap { newToken =>
            ZIO.logInfo(s"Retrying $endpoint with refreshed token...") *>
            makeApiCall[T](endpoint, newToken.access_token)
          }
      }
    } yield result
  }
  
  /**
   * Make a raw API call with automatic retry on 401 errors
   */
  def makeRawApiCallWithRetry(endpoint: String): Task[String] = {
    for {
      token <- ensureAuthenticated()
      result <- makeRawApiCall(endpoint, token.access_token).catchSome {
        case e: RuntimeException if e.getMessage.contains("401") =>
          ZIO.logInfo(s"Received 401 error for $endpoint, forcing token refresh...") *>
          ensureAuthenticated(forceRefresh = true).flatMap { newToken =>
            ZIO.logInfo(s"Retrying $endpoint with refreshed token...") *>
            makeRawApiCall(endpoint, newToken.access_token)
          }
      }
    } yield result
  }
  def getUserPreference(accessToken: String): Task[String] = 
    underlying.getUserPreference(accessToken)
  
  // Enhanced getToken that handles OAuth flow automatically
  def getToken(code: String): Task[TokenResponse] = underlying.getToken(code)
  
  /**
   * Get a valid access token, handling OAuth flow automatically if needed
   * @param forceRefresh if true, will refresh the token even if it appears valid
   */
  def ensureAuthenticated(forceRefresh: Boolean = false): Task[TokenResponse] = {
    tokenStorage.getTokenWithTimestamp().flatMap {
      case Some((token, timestamp)) if !forceRefresh && isTokenValid(token, timestamp) => 
        val age = (java.lang.System.currentTimeMillis() / 1000) - timestamp
        val remaining = token.expires_in.getOrElse(1800) - age.toInt
        ZIO.logInfo(s"Using existing valid token (age: ${age}s, remaining: ${remaining}s)") *>
        ZIO.succeed(token)
      case Some((token, timestamp)) if token.refresh_token.isDefined =>
        val reason = if (forceRefresh) "forced refresh" else {
          val age = (java.lang.System.currentTimeMillis() / 1000) - timestamp
          s"token expired/invalid (age: ${age}s)"
        }
        ZIO.logInfo(s"Refreshing token due to: $reason") *>
        refreshAndStoreToken(token.refresh_token.get).catchAll { err =>
          ZIO.logError(s"Token refresh failed: ${err.getMessage}. Initiating new OAuth flow...") *>
          initiateOAuthFlow()
        }
      case Some((token, _)) =>
        ZIO.logInfo("Token exists but no refresh token available, initiating OAuth flow") *>
        initiateOAuthFlow()
      case None => 
        ZIO.logInfo("No token found, initiating OAuth flow") *>
        initiateOAuthFlow()
    }
  }
  
  private def isTokenValid(token: TokenResponse, obtainedAt: Long): Boolean = {
    // Check if token has expired
    // Schwab tokens expire after expires_in seconds (typically 1800 seconds = 30 minutes)
    // We'll refresh 5 minutes early to be safe
    token.expires_in match {
      case Some(expiresIn) =>
        val currentTime = java.lang.System.currentTimeMillis() / 1000
        val expiresAt = obtainedAt + expiresIn
        val safeExpiresAt = expiresAt - 300 // Refresh 5 minutes early
        currentTime < safeExpiresAt
      case None =>
        // If no expires_in provided, assume token is valid for 25 minutes
        val currentTime = java.lang.System.currentTimeMillis() / 1000
        val elapsed = currentTime - obtainedAt
        elapsed < 1500 // 25 minutes
    }
  }
  
  private def refreshAndStoreToken(refreshToken: String): Task[TokenResponse] = {
    for {
      _ <- ZIO.logDebug(s"Attempting to refresh token...")
      newToken <- underlying.refreshToken(refreshToken).tapBoth(
        err => ZIO.logError(s"Token refresh failed: ${err.getMessage}"),
        token => ZIO.logInfo(s"Token refreshed successfully. New expiry: ${token.expires_in.getOrElse(1800)}s")
      )
      _ <- tokenStorage.storeToken(newToken)
      _ <- ZIO.logDebug("New token stored to disk")
    } yield newToken
  }
  
  private def initiateOAuthFlow(): Task[TokenResponse] = {
    for {
      _ <- ZIO.logInfo("Starting OAuth authentication flow...")
      
      // Start embedded HTTP server for OAuth callback
      authCompleted <- Promise.make[Throwable, TokenResponse]
      serverReady <- Promise.make[Throwable, Unit]
      serverFiber <- startAuthServer(authCompleted, serverReady).fork
      
      // Wait for server to be ready before opening browser
      _ <- serverReady.await
        .timeoutFail(new Exception("Server failed to start within 30 seconds"))(30.seconds)
        .tapError(err => ZIO.logError(s"Server startup error: ${err.getMessage}"))
      
      // Open browser to Schwab auth page (no response_type per Schwab support)
      authUrl = s"${config.authUrl}?client_id=${config.clientId}&redirect_uri=${config.redirectUri}"
      _ <- ZIO.logInfo(s"Opening browser for authentication...")
      _ <- BrowserLauncher.openBrowser(authUrl)
      
      // Wait for authentication to complete (with timeout)
      token <- authCompleted.await
        .timeoutFail(new Exception("OAuth authentication timeout (5 minutes)"))(5.minutes)
        .ensuring(serverFiber.interrupt)
      
      _ <- tokenStorage.storeToken(token)
      _ <- ZIO.logInfo("OAuth authentication completed successfully")
    } yield token
  }
  
  private def startAuthServer(authCompleted: Promise[Throwable, TokenResponse], serverReady: Promise[Throwable, Unit]): Task[Unit] = {
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
      
      serverConfig <- if (isHttps) {
        // Create self-signed certificate for HTTPS on Windows
        import cert.WindowsSSLSetup
        WindowsSSLSetup.createSelfSignedCertificate("127.0.0.1")
          .map(sslSetup => Server.Config.default.port(port).ssl(sslSetup.config))
          .tapBoth(
            err => ZIO.logError(s"Failed to create SSL certificate: ${err.getMessage}"),
            _ => ZIO.logInfo("Created self-signed certificate for HTTPS")
          )
      } else {
        ZIO.succeed(Server.Config.default.port(port))
      }
      
      // Start the server in a forked fiber to allow signaling when ready
      serverFiber <- Server.serve(Routes(authRoute).sandbox)
        .provide(
          ZLayer.succeed(serverConfig),
          Server.live
        )
        .tapError(err => ZIO.logError(s"Server startup error: ${err.getMessage}"))
        .fork
      
      // Give the server more time to bind to the port
      _ <- ZIO.sleep(2.seconds)
      
      // Test if the server is listening by attempting to connect
      _ <- testServerConnection(port)
        .retry(Schedule.recurs(10) && Schedule.spaced(500.millis))
        .tapBoth(
          err => ZIO.logError(s"Server failed to start after retries: ${err.getMessage}") *> serverReady.fail(err),
          _ => ZIO.logInfo("OAuth callback server is ready and listening") *> serverReady.succeed(())
        )
      
      // Keep the server running
      _ <- serverFiber.join
    } yield ()
  }
  
  private def testServerConnection(port: Int): Task[Unit] = {
    import java.net.{Socket, InetSocketAddress}
    ZIO.attempt {
      val socket = new Socket()
      try {
        socket.connect(new InetSocketAddress("127.0.0.1", port), 1000)
        socket.close()
      } finally {
        if (!socket.isClosed) socket.close()
      }
    }.mapError(e => new Exception(s"Cannot connect to port $port: ${e.getMessage}"))
  }
}

/**
 * Cross-platform browser launcher
 */
object BrowserLauncher {
  def openBrowser(url: String): Task[Unit] = {
    ZIO.attempt {
      if (Desktop.isDesktopSupported && Desktop.getDesktop.isSupported(Desktop.Action.BROWSE)) {
        Desktop.getDesktop.browse(new URI(url))
      } else {
        // Fallback for systems where Desktop is not supported
        val os = java.lang.System.getProperty("os.name").toLowerCase
        val process = os match {
          case s if s.contains("win") => 
            java.lang.Runtime.getRuntime.exec(Array("cmd", "/c", "start", "", url))
          case s if s.contains("mac") => 
            java.lang.Runtime.getRuntime.exec(Array("open", url))
          case s if s.contains("nix") || s.contains("nux") => 
            java.lang.Runtime.getRuntime.exec(Array("xdg-open", url))
          case _ => 
            throw new UnsupportedOperationException(s"Cannot open browser on OS: $os")
        }
        process.waitFor()
      }
    }.orDie.unit
  }
}

/**
 * Token storage trait for persisting OAuth tokens
 */
trait TokenStorage {
  def storeToken(token: TokenResponse): Task[Unit]
  def getToken(): Task[Option[TokenResponse]]
  def getTokenWithTimestamp(): Task[Option[(TokenResponse, Long)]]
}

/**
 * Stored token with timestamp
 */
case class StoredToken(token: TokenResponse, obtainedAt: Long)

object StoredToken {
  implicit val tokenResponseDecoder: JsonDecoder[TokenResponse] = DeriveJsonDecoder.gen[TokenResponse]
  implicit val tokenResponseEncoder: JsonEncoder[TokenResponse] = DeriveJsonEncoder.gen[TokenResponse]
  implicit val decoder: JsonDecoder[StoredToken] = DeriveJsonDecoder.gen[StoredToken]
  implicit val encoder: JsonEncoder[StoredToken] = DeriveJsonEncoder.gen[StoredToken]
}

/**
 * File-based token storage
 */
class FileTokenStorage(filePath: String = s"${java.lang.System.getProperty("user.home")}/.schwab_token.json") extends TokenStorage {
  
  import StoredToken._
  
  override def storeToken(token: TokenResponse): Task[Unit] = {
    ZIO.attempt {
      // Store in new format with timestamp
      val storedToken = StoredToken(token, java.lang.System.currentTimeMillis() / 1000)
      val json = storedToken.toJson
      val file = new File(filePath)
      file.getParentFile.mkdirs()
      val writer = new PrintWriter(file)
      try {
        writer.write(json)
      } finally {
        writer.close()
      }
    }
  }
  
  override def getToken(): Task[Option[TokenResponse]] = {
    getTokenWithTimestamp().map(_.map(_._1))
  }
  
  override def getTokenWithTimestamp(): Task[Option[(TokenResponse, Long)]] = {
    ZIO.attempt {
      val file = new File(filePath)
      if (file.exists()) {
        val content = new String(Files.readAllBytes(Paths.get(filePath)))
        
        // Parse as StoredToken (new format only)
        content.fromJson[StoredToken].toOption
      } else {
        None
      }
    }.flatMap {
      case Some(stored) => 
        val currentTime = java.lang.System.currentTimeMillis() / 1000
        val age = currentTime - stored.obtainedAt
        ZIO.logDebug(s"Token loaded from storage. Age: ${age}s, Expires in: ${stored.token.expires_in.getOrElse(1800)}s") *>
        ZIO.succeed(Some((stored.token, stored.obtainedAt)))
      case None =>
        ZIO.logDebug("No valid token found in storage") *>
        ZIO.succeed(None)
    }.catchAll { err =>
      ZIO.logError(s"Failed to read token from storage: ${err.getMessage}") *>
      ZIO.succeed(None)
    }
  }
}

object SeamlessSchwabClient {
  
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
      } yield new SeamlessSchwabClient(underlying, config, tokenStorage)
    }
  
  /**
   * Live layer with seamless OAuth - this is what apps should use
   */
  val live: ZLayer[Any, Throwable, SchwabClient] = {
    val configAndClient = SchwabApiConfig.live ++ Client.default
    configAndClient >>> layer
  }
  
  /**
   * Ensure authenticated before making API calls
   * @param forceRefresh if true, will refresh the token even if it appears valid
   */
  def ensureAuthenticated(forceRefresh: Boolean = false): ZIO[SchwabClient, Throwable, TokenResponse] =
    ZIO.serviceWithZIO[SchwabClient] {
      case client: SeamlessSchwabClient => client.ensureAuthenticated(forceRefresh)
      case _ => ZIO.fail(new Exception("Not a SeamlessSchwabClient"))
    }
}