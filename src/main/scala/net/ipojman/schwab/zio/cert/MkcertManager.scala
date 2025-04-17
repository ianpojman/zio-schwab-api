package net.ipojman.schwab.zio.cert

import zio.*
import zio.http.{SSLConfig, Server}

import java.io.{File, FileInputStream, FileOutputStream}
import java.lang.System.currentTimeMillis
import java.security.cert.CertificateFactory
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}
import java.security.{KeyStore, SecureRandom, Security}
import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext

object MkcertManager {
  def setupLocalCert(domain: String = "localhost"): ZIO[Any, Throwable, SSLSetup] = {
    ZIO.attemptBlocking {
      // First, install the local CA if it's not already installed
      val caInstallCommand = Seq("mkcert", "-install")
      println("\n============ INSTALLING LOCAL CA ============")
      println("Attempting to install the local CA certificate...")
      println("If prompted for your password, please enter it.")
      println("Command: " + caInstallCommand.mkString(" "))
      println("===========================================\n")

      val caInstallProcess = new ProcessBuilder(caInstallCommand: _*).inheritIO().start()
      val caInstallExitCode = caInstallProcess.waitFor()
      if (caInstallExitCode != 0) {
        println("Warning: Could not automatically install the local CA.")
        println("Please run this command manually: sudo mkcert -install")
      } else {
        println("Local CA installed successfully!")
      }

      // Clean up any existing files to avoid confusion
      println("\n============ CLEANING UP OLD FILES ============")
      val dir = new File(".")
      val oldFiles = dir.listFiles().filter(f =>
        (f.getName.startsWith("localhost+") && f.getName.endsWith(".pem")) ||
          f.getName == "localhost.p12" ||
          f.getName == "server-cert.crt"
      )
      oldFiles.foreach { f =>
        println(s"Deleting old file: ${f.getName}")
        f.delete()
      }
      println("===========================================\n")

      // Generate the certificate for your domains
      println("\n============ GENERATING CERTIFICATE ============")
      val mkcertGenCommand = Seq("mkcert", domain, "127.0.0.1", "::1")
      println("Generating certificate for: " + mkcertGenCommand.drop(1).mkString(", "))
      println("Command: " + mkcertGenCommand.mkString(" "))
      println("==============================================\n")

      val mkcertGenProcess = new ProcessBuilder(mkcertGenCommand: _*).inheritIO().start()
      val genExitCode = mkcertGenProcess.waitFor()
      if (genExitCode != 0) {
        throw new RuntimeException("Failed to generate certificate with mkcert")
      }

      // Find the actual filenames that mkcert generated - wait a moment for files to be written
      Thread.sleep(1000)
      val certFiles = dir.listFiles().filter(f =>
        f.getName.startsWith("localhost+") && f.getName.endsWith(".pem")
      )

      if (certFiles.isEmpty) {
        throw new RuntimeException("Could not find any certificate files")
      }

      // Determine cert and key files by looking at the newest files
      val certFile = certFiles.find(f => !f.getName.contains("key") && f.lastModified() > currentTimeMillis() - 5000).getOrElse(
        throw new RuntimeException("Could not find certificate file")
      )

      val keyFile = certFiles.find(f => f.getName.contains("key") && f.lastModified() > currentTimeMillis() - 5000).getOrElse(
        throw new RuntimeException("Could not find key file")
      )

      println(s"Found certificate file: ${certFile.getAbsolutePath}")
      println(s"Found key file: ${keyFile.getAbsolutePath}")

      // Convert to PKCS12 for ZIO HTTP
      val keystorePath = s"$domain.p12"
      val password = "password"
      val opensslCommand = Seq(
        "openssl", "pkcs12", "-export",
        "-out", keystorePath,
        "-inkey", keyFile.getAbsolutePath,
        "-in", certFile.getAbsolutePath,
        "-password", s"pass:$password"
      )

      println("\n============ CONVERTING TO PKCS12 ============")
      println("Converting certificate to PKCS12 format...")
      println("Command: " + opensslCommand.mkString(" "))
      println("===========================================\n")

      val opensslProcess = new ProcessBuilder(opensslCommand: _*).inheritIO().start()
      val convertExitCode = opensslProcess.waitFor()
      if (convertExitCode != 0) {
        throw new RuntimeException("Failed to convert certificate to PKCS12")
      }

      // Load certificate from file
      val certFactory = CertificateFactory.getInstance("X.509")
      val certificate = certFactory.generateCertificate(new FileInputStream(certFile))
        .asInstanceOf[java.security.cert.X509Certificate]

      // Export the certificate for manual installation
      val exportCertCommand = Seq(
        "openssl", "x509",
        "-in", certFile.getAbsolutePath,
        "-out", "server-cert.crt"
      )

      val exportProcess = new ProcessBuilder(exportCertCommand: _*).inheritIO().start()
      val exportExitCode = exportProcess.waitFor()

      if (exportExitCode == 0) {
        println("[Schwab API] Certificate exported to server-cert.crt")
        println("[Schwab API] To trust this certificate in Safari, run:")
        println("sudo security add-trusted-cert -d -r trustRoot -k /Library/Keychains/System.keychain server-cert.crt")
      }

      // Create a custom SSLContext that only supports TLS 1.2
      val keyStore = KeyStore.getInstance("PKCS12")
      val keyStoreInputStream = new FileInputStream(keystorePath)
      keyStore.load(keyStoreInputStream, password.toCharArray)
      keyStoreInputStream.close()

      val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
      keyManagerFactory.init(keyStore, password.toCharArray)

      val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
      trustManagerFactory.init(keyStore)

      // Create SSLContext explicitly for TLS 1.2
      val sslContext = SSLContext.getInstance("TLSv1.2")
      sslContext.init(keyManagerFactory.getKeyManagers, trustManagerFactory.getTrustManagers, new SecureRandom())

      // Set this SSLContext as the default for all new SSL connections
      SSLContext.setDefault(sslContext)

      // Also set the JVM-wide properties for TLS
      java.lang.System.setProperty("https.protocols", "TLSv1.2")
      java.lang.System.setProperty("jdk.tls.client.protocols", "TLSv1.2")
      java.lang.System.setProperty("jdk.tls.server.protocols", "TLSv1.2")

      // Disable all SSL/TLS protocols except TLS 1.2 in security properties
      Security.setProperty("jdk.tls.disabledAlgorithms", "SSLv2Hello, SSLv3, TLSv1, TLSv1.1, TLSv1.3")

      val config2 = SSLConfig.fromJavaxNetSslKeyStoreFile(
        keystorePath,
        Some(Config.Secret(password)),
        keyManagerKeyStoreType = "PKCS12"
      )
      // Create SSL config for ZIO HTTP
      val sslConfig = Server.Config.default
        .port(8443)
        .ssl(
          config2
        )

      println("\n============ CERTIFICATE SETUP COMPLETE ============")
      println(s"Server configured to use SSL at https://localhost:8443")
      println(s"Certificate file: ${certFile.getName}")
      println(s"Key file: ${keyFile.getName}")
      println(s"Keystore file: $keystorePath")
      println(s"Protocol: Forced TLSv1.2 only via multiple configuration methods")
      println("If Safari still shows SSL errors, please:")
      println("1. Make sure you open https://localhost:8443 (not 127.0.0.1)")
      println("2. Try restarting Safari")
      println("3. If issues persist, check Safari → Preferences → Privacy → Manage Website Data → Remove All")
      println("================================================\n")

      SSLSetup(config2, certificate)
    }
  }
}