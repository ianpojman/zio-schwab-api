package net.ipojman.schwab.zio.cert

import zio.*
import zio.http.*
import zio.test.*

import java.io.FileInputStream
import java.nio.file.{Files, Path}
import java.security.KeyStore

object CertificateManagerSpec extends ZIOSpecDefault {

  private val password = "test-password"

  private def loadKeyStore(path: Path): Task[KeyStore] = ZIO.attempt {
    val store = KeyStore.getInstance("PKCS12")
    val in = new FileInputStream(path.toFile)
    try store.load(in, password.toCharArray) finally in.close()
    store
  }

  // The repo keeps no keystore: the local callback server makes its own at first start.
  def spec = suite("CertificateManagerSpec")(
    test("with no keystore file, getSslConfig makes one whose certificate is valid now for 127.0.0.1, and an https server starts on it") {
      for {
        dir   <- ZIO.attempt(Files.createTempDirectory("certificate-manager-spec"))
        path   = dir.resolve("keystore.p12")
        before = Files.exists(path)
        setup <- CertificateManager.getSslConfig(port = 8443, keystorePath = path.toString, password = password)
        store <- loadKeyStore(path)
        _     <- ZIO.attempt(setup.certificate.checkValidity())
        port  <- Server.install(Routes.empty).provide(ZLayer.succeed(Server.Config.default.port(0).ssl(setup.config)), Server.live)
        _     <- ZIO.attempt { Files.deleteIfExists(path); Files.deleteIfExists(dir) }
      } yield assertTrue(
        !before,
        store.isKeyEntry("server"),
        setup.certificate.getSubjectX500Principal.getName.contains("CN=127.0.0.1"),
        port > 0
      )
    }
  )
}
