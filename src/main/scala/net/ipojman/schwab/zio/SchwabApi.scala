package net.ipojman.schwab.zio

import zio.*
import zio.http.*
import zio.json.*
import zio.logging.*
import zio.logging.backend.SLF4J
import net.ipojman.schwab.zio.models.{SchwabApiResponse, SchwabErrorResponse}

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
  implicit val encoder: JsonEncoder[TokenResponse] = DeriveJsonEncoder.gen[TokenResponse]
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

  /** Make an API call that can handle both success and error responses */
  def makeApiCallSafe[T: JsonDecoder](endpoint: String, accessToken: String): Task[SchwabApiResponse[T]]

  /** Make a raw API call to the Schwab API */
  def makeRawApiCall(endpoint: String, accessToken: String): Task[String]
  
  /** Get user preferences including account nicknames */
  def getUserPreference(accessToken: String): Task[String]

  /**
   * Send a write call (POST, PUT or DELETE) to the Schwab API, once.
   *
   * A non-2xx reply fails with [[SchwabHttpError]]. Nothing here retries: a write the
   * broker may have acted on must not be sent twice.
   *
   * The default fails, so a client that only wraps the read calls refuses writes
   * until it overrides this.
   *
   * @param jsonBody the JSON request body, or None for a call without one (DELETE)
   */
  def makeWriteApiCall(
    method: Method,
    endpoint: String,
    accessToken: String,
    jsonBody: Option[String]
  ): Task[ApiWriteResponse] =
    ZIO.fail(new UnsupportedOperationException(s"${getClass.getSimpleName} does not send write calls"))
}

/**
 * Reply to a write call that came back 2xx.
 *
 * @param status the HTTP status code
 * @param location the Location header, which for a placed order names the new order
 * @param body the response body, often empty
 */
case class ApiWriteResponse(status: Int, location: Option[String], body: String)

/**
 * A non-2xx reply from the Schwab API, with its status code and body.
 */
class SchwabHttpError(val status: Int, val url: String, val body: String)
  extends RuntimeException(s"API error: $status for URL: ${LiveSchwabClient.maskAccount(url)} - Response body: $body")

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

  def makeApiCallSafe[T: JsonDecoder](endpoint: String, accessToken: String): ZIO[SchwabClient, Throwable, SchwabApiResponse[T]] =
    ZIO.serviceWithZIO[SchwabClient](_.makeApiCallSafe[T](endpoint, accessToken))

  def makeRawApiCall(endpoint: String, accessToken: String): ZIO[SchwabClient, Throwable, String] =
    ZIO.serviceWithZIO[SchwabClient](_.makeRawApiCall(endpoint, accessToken))
    
  def getUserPreference(accessToken: String): ZIO[SchwabClient, Throwable, String] =
    ZIO.serviceWithZIO[SchwabClient](_.getUserPreference(accessToken))

  /**
   * Live implementation of the SchwabClient (basic, no OAuth handling)
   */
  val live: ZLayer[SchwabApiConfig & Client, Nothing, SchwabClient] =
    ZLayer.fromFunction(LiveSchwabClient(_, _))
  
  /**
   * Seamless implementation that handles OAuth automatically (recommended for most apps)
   */
  val seamless: ZLayer[Any, Throwable, SchwabClient] = SeamlessSchwabClient.live
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
      _         <- ZIO.logDebug("Exchanging an authorization code for a token")
      response  <- client.request(request).provide(ZLayer.succeed(Scope.global))
      _         <- ZIO.logDebug(s"Token response status: ${response.status}")
      bodyStr   <- response.body.asString
      tokenResp <- ZIO.fromEither(bodyStr.fromJson[TokenResponse])
        .mapError(err => new RuntimeException(s"Failed to parse token response: $err"))
        .tapError(err => ZIO.logError(err.getMessage))
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
      tokenResp <- ZIO.fromEither(bodyStr.fromJson[TokenResponse])
        .mapError(err => new RuntimeException(s"Failed to parse token response: $err"))
    } yield tokenResp
  }

  def makeApiCall[T: JsonDecoder](endpoint: String, accessToken: String): Task[T] = {
    makeRawApiCall(endpoint, accessToken).flatMap { response =>
      ZIO.logDebug(s"API response body length: ${response.length}") *>
      ZIO.fromEither(response.fromJson[T])
        .mapError(err => new RuntimeException(s"Failed to parse API response: $err (response body length: ${response.length})"))
    }
  }

  def makeApiCallSafe[T: JsonDecoder](endpoint: String, accessToken: String): Task[SchwabApiResponse[T]] = {
    makeRawApiCall(endpoint, accessToken).flatMap { response =>
      ZIO.logDebug(s"API response body length: ${response.length}") *> {
        // First try to parse as error response
        response.fromJson[SchwabErrorResponse] match {
          case Right(errorResponse) =>
            ZIO.logWarning(s"Schwab API returned error response: ${errorResponse.errors.map(_.message).mkString(", ")}") *>
            ZIO.succeed(SchwabApiResponse.Error(errorResponse.errors))
          case Left(_) =>
            // Not an error response, try to parse as success type
            ZIO.fromEither(response.fromJson[T])
              .mapBoth(
                err => new RuntimeException(s"Failed to parse API response as both error and success types: $err (response body length: ${response.length})"),
                data => SchwabApiResponse.Success(data)
              )
        }
      }
    }
  }

  def makeRawApiCall(endpoint: String, accessToken: String): Task[String] = {
    val fullUrl = s"${config.apiBaseUrl}/${endpoint.stripPrefix("/")}"

    // Use Path.decode instead of URL
    val request = Request.get(fullUrl)
      .addHeader(Header.Authorization.Bearer(accessToken))

    for {
      _        <- ZIO.logDebug(s"Making Schwab API call: ${LiveSchwabClient.maskAccount(fullUrl)}")
      response <- client.request(request).provide(ZLayer.succeed(Scope.global))
      _        <- ZIO.logDebug(s"API response status: ${response.status}")
      body     <- response.body.asString
      _        <- ZIO.when(response.status.isError)(
        if (response.status.code == 401) {
          ZIO.logError(s"Authentication error (401) for URL: ${LiveSchwabClient.maskAccount(fullUrl)} - Token may be expired")
        } else {
          ZIO.logError(s"API error: ${response.status} for URL: ${LiveSchwabClient.maskAccount(fullUrl)} - Response body: $body")
        }
      )
      _        <- ZIO.logTrace(s"API response body length: ${body.length}")
      _        <- ZIO.fail(new RuntimeException(s"API error: ${response.status} for URL: ${LiveSchwabClient.maskAccount(fullUrl)} - Response body: $body"))
        .when(response.status.isError)
    } yield body
  }
  
  def getUserPreference(accessToken: String): Task[String] = {
    makeRawApiCall("trader/v1/userPreference", accessToken)
  }

  override def makeWriteApiCall(
    method: Method,
    endpoint: String,
    accessToken: String,
    jsonBody: Option[String]
  ): Task[ApiWriteResponse] = {
    val fullUrl = s"${config.apiBaseUrl}/${endpoint.stripPrefix("/")}"
    for {
      request  <- LiveSchwabClient.writeRequest(method, fullUrl, accessToken, jsonBody)
      _        <- ZIO.logDebug(s"Making Schwab API write call: $method ${LiveSchwabClient.maskAccount(fullUrl)}")
      response <- client.request(request).provide(ZLayer.succeed(Scope.global))
      result   <- LiveSchwabClient.readWriteResponse(fullUrl, response)
    } yield result
  }
}

object LiveSchwabClient {

  /**
   * `url` with the path segment after `accounts/` replaced by a fixed mask, for log lines. On the trader paths that
   * segment is the account hash, which identifies the account and does not belong in a log.
   */
  def maskAccount(url: String): String = url.replaceAll("(/accounts/)[^/?#]+", "$1***")

  /** Build the request for a write call: bearer token, and a JSON body when there is one. */
  def writeRequest(method: Method, fullUrl: String, accessToken: String, jsonBody: Option[String]): Task[Request] =
    ZIO.fromEither(URL.decode(fullUrl)).map { url =>
      val bare = Request(method = method, url = url)
        .addHeader(Header.Authorization.Bearer(accessToken))
        .addHeader(Header.Accept(MediaType.application.json))
      jsonBody match {
        case Some(json) =>
          bare.addHeader(Header.ContentType(MediaType.application.json)).copy(body = Body.fromString(json))
        case None => bare
      }
    }

  /** Turn a write call's reply into an [[ApiWriteResponse]], or fail with [[SchwabHttpError]] on a non-2xx status. */
  def readWriteResponse(fullUrl: String, response: Response): Task[ApiWriteResponse] =
    for {
      body <- response.body.asString
      _    <- ZIO.when(!response.status.isSuccess)(
                ZIO.logError(s"API error: ${response.status.code} for URL: ${maskAccount(fullUrl)} - Response body: $body") *>
                ZIO.fail(new SchwabHttpError(response.status.code, fullUrl, body))
              )
    } yield ApiWriteResponse(response.status.code, response.rawHeader("Location"), body)
}
