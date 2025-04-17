package net.ipojman.schwab.zio

import zio.{Config, ConfigProvider, Layer, ZIO, ZLayer}

/**
 * Configuration for the Schwab API
 */
case class SchwabApiConfig(
                            clientId: String,
                            clientSecret: String,
                            tokenUrl: String = "https://api.schwabapi.com/v1/oauth/token",
                            redirectUri: String = "https://127.0.0.1:8443/api/oauth/callback", // Updated default
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

  // New factory method for creating config with custom redirect URI
  def withRedirectUri(newRedirectUri: String): ZLayer[SchwabApiConfig, Nothing, SchwabApiConfig] =
    ZLayer.fromFunction((config:SchwabApiConfig) => config.copy(redirectUri = newRedirectUri))

  // Convenience method that creates a config with default values and a custom redirect URI
  def custom(
              clientId: String,
              clientSecret: String,
              redirectUri: String,
              tokenUrl: String = "https://api.schwabapi.com/v1/oauth/token",
              authUrl: String = "https://api.schwabapi.com/v1/oauth/authorize",
              apiBaseUrl: String = "https://api.schwabapi.com"
            ): ZLayer[Any, Nothing, SchwabApiConfig] =
    ZLayer.succeed(
      SchwabApiConfig(
        clientId = clientId,
        clientSecret = clientSecret,
        tokenUrl = tokenUrl,
        redirectUri = redirectUri,
        authUrl = authUrl,
        apiBaseUrl = apiBaseUrl
      )
    )
}