package net.ipojman.schwab.zio

import net.ipojman.schwab.zio.cert.{CertificateManager, MkcertManager}
import zio.*
import zio.http.*
import zio.json.*
import zio.logging.*
import zio.logging.backend.SLF4J
import zio.Config

import java.io.{File, FileOutputStream}
import java.math.BigInteger
import java.security.{KeyPair, KeyPairGenerator, KeyStore, SecureRandom}
import java.time.{Instant, ZoneId}
import java.time.temporal.ChronoUnit
import java.util.Date
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.{BasicConstraints, Extension, GeneralName, GeneralNames}
import org.bouncycastle.cert.jcajce.{JcaX509CertificateConverter, JcaX509v3CertificateBuilder}
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

import java.lang.System.currentTimeMillis

/**
 * Complete Schwab API Server with OAuth flow handled through HTTPS
 * All components in one file for easy deployment
 */
object SchwabHttpsServerComplete extends ZIOAppDefault {
  // Configure logging with SLF4J
  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  // Initialize BouncyCastle provider
  java.security.Security.addProvider(new BouncyCastleProvider())

  // ====================== Certificate Manager ======================

  // ====================== Token Repository ======================


  /**
   * Token storage interface
   */
  trait TokenRepository {
    def storeToken(token: TokenResponse): Task[Unit]
    def getToken: Task[TokenResponse]
    def getAccessToken: Task[String]
  }

  /**
   * In-memory implementation
   */
  case class InMemoryTokenRepository(tokenRef: Ref[Option[TokenResponse]]) extends TokenRepository {
    override def storeToken(token: TokenResponse): Task[Unit] =
      tokenRef.set(Some(token))

    override def getToken: Task[TokenResponse] =
      tokenRef.get.flatMap {
        case Some(token) => ZIO.succeed(token)
        case None => ZIO.fail(new RuntimeException("No token available"))
      }

    override def getAccessToken: Task[String] =
      getToken.map(_.access_token)
  }

  /**
   * Token repository companion object
   */
  object TokenRepository {
    val inMemory: ZLayer[Any, Nothing, TokenRepository] =
      ZLayer.fromZIO(
        Ref.make(Option.empty[TokenResponse]).map(InMemoryTokenRepository(_))
      )

    def storeToken(token: TokenResponse): ZIO[TokenRepository, Throwable, Unit] =
      ZIO.serviceWithZIO[TokenRepository](_.storeToken(token))

    def getToken: ZIO[TokenRepository, Throwable, TokenResponse] =
      ZIO.serviceWithZIO[TokenRepository](_.getToken)

    def getAccessToken: ZIO[TokenRepository, Throwable, String] =
      ZIO.serviceWithZIO[TokenRepository](_.getAccessToken)
  }

  // ====================== Auth Code Extractor ======================

  /**
   * Extracts the authorization code from a callback URL
   */
  object AuthCodeExtractor {
    def extractCode(url: String): String = {
      if (url.contains("code=")) {
        // Extract code from code= to %40 (which is the URL-encoded @ symbol)
        val codeStart = url.indexOf("code=") + 5
        val codeEnd = url.indexOf("%40")

        if (codeEnd > codeStart) {
          s"${url.substring(codeStart, codeEnd)}@"
        } else {
          // If %40 is not found, try to extract until the next & or end of string
          val altCodeEnd = url.indexOf("&", codeStart)
          if (altCodeEnd > codeStart) {
            url.substring(codeStart, altCodeEnd)
          } else {
            url.substring(codeStart)
          }
        }
      } else {
        // If no "code=" found, assume the entire string is the code
        url
      }
    }
  }

//
//  case class MockSchwabClient() extends SchwabClient {
//    override def authorize: Task[String] =
//      ZIO.succeed("https://api.schwab.com/authorize?client_id=123&redirect_uri=https://127.0.0.1:8443/api/oauth/callback")
//
//    override def getToken(code: String): Task[TokenResponse] =
//      ZIO.succeed(TokenResponse("mock-access-token", Some("3600"), Some("bearer")))
//
//    override def makeRawApiCall(path: String, token: String): Task[String] =
//      ZIO.succeed("""{"accounts": [{"id": "123", "name": "Test Account", "balance": 10000}]}""")
//  }


  // ====================== Server Routes ======================

  private def escapeHtml(text: String): String =
    text.replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&#39;")

  // Define routes using the example format
  val apiRoutes: Routes[SchwabClient & TokenRepository, Response] =
    Routes(
      Method.GET / "" ->
        handler { (_: Request) =>
          ZIO.succeed(
            Response.html(
              """
                |<html><head><title>Schwab API Demo</title></head>
                |<body>
                |  <h1>Schwab API OAuth Demo</h1>
                |  <p>Click the button below to start the authentication process:</p>
                |  <a href="/api/login">Login with Schwab</a>
                |</body></html>
              """.stripMargin
            )
          )
        },

      Method.GET / "api" / "login" ->
        handler { (_: Request) =>
          for {
            authUrl <- SchwabClient.authorize
            _ <- ZIO.logDebug(s"Redirecting to Schwab authorization: $authUrl")
            redirectUrl <- ZIO.fromEither(URL.decode(authUrl))
              .orElseFail(new RuntimeException(s"Invalid URL: $authUrl"))
          } yield Response.redirect(redirectUrl)
        },

      Method.GET / "api" / "oauth" / "callback" ->
        handler { (req: Request) =>
          for {
            _ <- ZIO.logDebug(s"Received OAuth callback: ${req.url}")
            codeParam = req.url.queryParams.getAll("code").headOption.getOrElse(sys.error("Code not found"))
            tokenResponse <- SchwabClient.getToken(codeParam)
            _ <- TokenRepository.storeToken(tokenResponse)
          } yield Response.html(
            """<html>
              |<head>
              |  <title>Authentication Successful</title>
              |  <style>
              |    body { 
              |      font-family: Arial, sans-serif; 
              |      text-align: center; 
              |      padding: 50px;
              |      background-color: #f5f5f5;
              |    }
              |    h1 { 
              |      color: #4CAF50; 
              |      margin-bottom: 20px;
              |    }
              |    p {
              |      margin-bottom: 20px;
              |      color: #666;
              |    }
              |    a {
              |      display: inline-block;
              |      padding: 10px 20px;
              |      background-color: #007bff;
              |      color: white;
              |      text-decoration: none;
              |      border-radius: 5px;
              |      margin-top: 20px;
              |    }
              |    a:hover {
              |      background-color: #0056b3;
              |    }
              |  </style>
              |</head>
              |<body>
              |  <h1>Authentication Successful!</h1>
              |  <p>You have successfully authenticated with Schwab API.</p>
              |  <a href="/api/accounts">View Accounts</a>
              |  <script>
              |    setTimeout(function() { window.close(); }, 2000);
              |  </script>
              |</body>
              |</html>""".stripMargin
          )
        },

      Method.GET / "api" / "accounts" ->
        handler { (_: Request) =>
          for {
            token <- TokenRepository.getAccessToken
            accountsJson <- SchwabClient.makeRawApiCall("trader/v1/accounts", token)
          } yield Response.html(
            s"""
               |<html><head><title>Accounts</title></head>
               |<body>
               |  <h1>Your Schwab Accounts</h1>
               |  <pre>${escapeHtml(accountsJson)}</pre>
               |</body></html>
            """.stripMargin
          )
        }
    ).sandbox

  // ====================== Main Application ======================

  def run = {
    val program = for {
      // Generate certificate and get SSL config
      _ <- Console.printLine("[Schwab API] Starting server with HTTPS support")
      sslConfig <- MkcertManager.setupLocalCert()

      // Log server details
      _ <- Console.printLine("[Schwab API] Certificate prepared successfully")
      _ <- Console.printLine("[Schwab API] Server running at: https://127.0.0.1:8443")
      _ <- Console.printLine("[Schwab API] OAuth callback URL: https://127.0.0.1:8443/api/oauth/callback")
      _ <- Console.printLine("[Schwab API] Make sure this callback URL is registered in the Schwab developer portal")

      // Start the server
      _ <- Server.serve(apiRoutes)
        .provide(
          ZLayer.succeed(sslConfig),
          SchwabApiConfig.live,
          Client.default,
          SchwabClient.live,
          TokenRepository.inMemory,
          ZLayer.succeed(Server.Config.default.ssl(sslConfig.config).port(8443)),
          Server.live
        )
    } yield ()

    program.exitCode
  }
}