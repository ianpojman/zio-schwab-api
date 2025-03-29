package net.ipojman.schwab.zio

import zio.*
import zio.http.*
import zio.json.*
import zio.logging.*
import zio.logging.backend.SLF4J

/**
 * Response from Schwab OAuth token endpoint
 *
 * @param expires_in Time in seconds until the token expires
 * @param token_type Type of token (e.g., "Bearer")
 * @param access_token The access token to use for API calls
 * @param refresh_token Token that can be used to refresh the access token
 * @param scope Scopes granted to this token
 */
case class TokenResponse(
                          access_token: String,
                          token_type: Option[String] = None,
                          expires_in: Option[Int] = None,
                          refresh_token: Option[String] = None, // not provided if using client credentials
                          scope: Option[String] = None,
                          id_token: Option[String] = None // Added based on Python implementation
                        )

object TokenResponse {
  implicit val decoder: JsonDecoder[TokenResponse] = DeriveJsonDecoder.gen[TokenResponse]
}

/**
 * Configuration for the Schwab API
 */
case class SchwabApiConfig(
                            clientId: String,
                            clientSecret: String,
                            tokenUrl: String = "https://api.schwabapi.com/v1/oauth/token",
                            redirectUri: String = "https://127.0.0.1",
                            authUrl: String = "https://api.schwabapi.com/v1/oauth/authorize",
                            apiBaseUrl: String = "https://api.schwabapi.com"
                          )

object SchwabApiConfig {
  import zio.config.*
  import zio.config.magnolia.*
  import zio.config.typesafe.*

  private val configDesc = deriveConfig[SchwabApiConfig]

  val fromEnv: Layer[Config.Error, SchwabApiConfig] =
    ZLayer.fromZIO(
      ConfigProvider.envProvider.load(configDesc) // Removed parentheses
    )

  val fromFile: ZLayer[Any, Throwable, SchwabApiConfig] =
    ZLayer.fromZIO(
      ZIO.attempt {
        val homeDir = sys.props.getOrElse("user.home", "")
        val configFile = new java.io.File(s"$homeDir/.schwab.conf")
        ConfigProvider.fromHoconFile(configFile).load(configDesc)
      }.flatten
    )

  val live: ZLayer[Any, Throwable, SchwabApiConfig] =
    fromEnv.orElse(fromFile)
}

/**
 * Main API client for Schwab
 */
trait SchwabClient {
  /** Get the authorization URL for the OAuth flow */
  def authorize: UIO[String]

  /** Get a token using authorization code grant */
  def getToken(code: String): Task[TokenResponse]

  /** Refresh an existing token */
  def refreshToken(refreshToken: String): Task[TokenResponse]

  /** Get an access token using client credentials */
  def getAccessToken: Task[TokenResponse]

  /** Make an API call to the Schwab API */
  def makeApiCall[T: JsonDecoder](endpoint: String, accessToken: String): Task[T]

  /** Make a raw API call to the Schwab API */
  def makeRawApiCall(endpoint: String, accessToken: String): Task[String]
}

object SchwabClient {
  def authorize: ZIO[SchwabClient, Throwable, String] =
    ZIO.serviceWithZIO[SchwabClient](_.authorize)

  def getToken(code: String): ZIO[SchwabClient, Throwable, TokenResponse] =
    ZIO.serviceWithZIO[SchwabClient](_.getToken(code))

  def refreshToken(refreshToken: String): ZIO[SchwabClient, Throwable, TokenResponse] =
    ZIO.serviceWithZIO[SchwabClient](_.refreshToken(refreshToken))

  def getAccessToken: ZIO[SchwabClient, Throwable, TokenResponse] =
    ZIO.serviceWithZIO[SchwabClient](_.getAccessToken)

  def makeApiCall[T: JsonDecoder](endpoint: String, accessToken: String): ZIO[SchwabClient, Throwable, T] =
    ZIO.serviceWithZIO[SchwabClient](_.makeApiCall[T](endpoint, accessToken))

  def makeRawApiCall(endpoint: String, accessToken: String): ZIO[SchwabClient, Throwable, String] =
    ZIO.serviceWithZIO[SchwabClient](_.makeRawApiCall(endpoint, accessToken))

  /**
   * Live implementation of the SchwabClient
   */
  val live: ZLayer[SchwabApiConfig & Client, Nothing, SchwabClient] =
    ZLayer.fromFunction(LiveSchwabClient(_, _))
}

/**
 * Live implementation of the SchwabClient
 */
case class LiveSchwabClient(config: SchwabApiConfig, client: Client) extends SchwabClient {

  def authorize: UIO[String] = {
    val params = Map(
      "client_id" -> config.clientId,
      "redirect_uri" -> config.redirectUri
    )
    ZIO.succeed(
      config.authUrl + "?" + params.map { case (k, v) => s"$k=$v" }.mkString("&")
    )
  }

  def getToken(code: String): Task[TokenResponse] = {
    // Form creation in ZIO-HTTP 3.2.0
    val formFields = List(
      FormField.simpleField("grant_type", "authorization_code"),
      FormField.simpleField("code", code),
      FormField.simpleField("redirect_uri", config.redirectUri)
    )

    val body = Body.fromURLEncodedForm(Form(Chunk.fromIterable(formFields)))

    // Use Path.decode instead of URL
    val request = Request.post(config.tokenUrl, body)
      .addHeader(Header.Authorization.Basic(config.clientId, config.clientSecret))

    for {
      _         <- ZIO.logDebug(s"Getting token with code: $code")
      response  <- client.request(request).provide(ZLayer.succeed(Scope.global))
      _         <- ZIO.logDebug(s"Token response status: ${response.status}")
      bodyStr   <- response.body.asString
      _         <- ZIO.logTrace(s"Token response body: $bodyStr")
      tokenResp <- ZIO.fromEither(bodyStr.fromJson[TokenResponse])
        .mapError(err => new RuntimeException(s"Failed to parse token response: $err"))
        .tapError(err => ZIO.logError(s"Failed to parse token response while parsing response body: \n---\n$bodyStr\n---\n$err"))
    } yield tokenResp
  }

  def refreshToken(refreshToken: String): Task[TokenResponse] = {
    // Form creation in ZIO-HTTP 3.2.0
    val formFields = List(
      FormField.simpleField("grant_type", "refresh_token"),
      FormField.simpleField("refresh_token", refreshToken)
    )

    val body = Body.fromURLEncodedForm(Form(Chunk.fromIterable(formFields)))

    // Use Path.decode instead of URL
    val request = Request.post(config.tokenUrl, body)
      .addHeader(Header.Authorization.Basic(config.clientId, config.clientSecret))

    for {
      _         <- ZIO.logDebug(s"Refreshing token")
      response  <- client.request(request).provide(ZLayer.succeed(Scope.global))
      _         <- ZIO.logDebug(s"Refresh token response status: ${response.status}")
      bodyStr   <- response.body.asString
      _         <- ZIO.logTrace(s"Refresh token response body: $bodyStr")
      tokenResp <- ZIO.fromEither(bodyStr.fromJson[TokenResponse])
        .mapError(err => new RuntimeException(s"Failed to parse token response: $err"))
    } yield tokenResp
  }

  def getAccessToken: Task[TokenResponse] = {
    // Form creation in ZIO-HTTP 3.2.0
    val formFields = List(
      FormField.simpleField("grant_type", "client_credentials")
    )

    val body = Body.fromURLEncodedForm(Form(Chunk.fromIterable(formFields)))

    // Use Path.decode instead of URL
    val request = Request.post(config.tokenUrl, body)
      .addHeader(Header.Authorization.Basic(config.clientId, config.clientSecret))

    for {
      _         <- ZIO.logDebug("Getting access token via client credentials")
      response  <- client.request(request).provide(ZLayer.succeed(Scope.global))
      _         <- ZIO.logDebug(s"Access token response status: ${response.status}")
      bodyStr   <- response.body.asString
      _         <- ZIO.logTrace(s"Access token response body: $bodyStr")
      tokenResp <- ZIO.fromEither(bodyStr.fromJson[TokenResponse])
        .mapError(err => new RuntimeException(s"Failed to parse token response: $err"))
    } yield tokenResp
  }

  def makeApiCall[T: JsonDecoder](endpoint: String, accessToken: String): Task[T] = {
    makeRawApiCall(endpoint, accessToken).flatMap { response =>
      ZIO.fromEither(response.fromJson[T])
        .mapError(err => new RuntimeException(s"Failed to parse API response: $err"))
    }
  }

  def makeRawApiCall(endpoint: String, accessToken: String): Task[String] = {
    val fullUrl = s"${config.apiBaseUrl}/${endpoint.stripPrefix("/")}"

    // Use Path.decode instead of URL
    val request = Request.get(fullUrl)
      .addHeader(Header.Authorization.Bearer(accessToken))

    for {
      _        <- ZIO.logDebug(s"Making Schwab API call: $fullUrl")
      response <- client.request(request).provide(ZLayer.succeed(Scope.global))
      _        <- ZIO.logDebug(s"API response status: ${response.status}")
      body     <- response.body.asString
      _        <- ZIO.when(response.status.isError)(
        ZIO.logError(s"API error: ${response.status} - $body")
      )
      _        <- ZIO.logTrace(s"API response body: $body")
      _        <- ZIO.fail(new RuntimeException(s"API error: ${response.status} - $body"))
        .when(response.status.isError)
    } yield body
  }
}
