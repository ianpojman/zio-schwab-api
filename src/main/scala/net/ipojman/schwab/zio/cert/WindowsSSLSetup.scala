package net.ipojman.schwab.zio.cert

import zio.*
import zio.http.SSLConfig
import javax.net.ssl.*
import java.security.*
import java.security.cert.X509Certificate
import java.io.FileOutputStream
import zio.Config

/**
 * Windows-friendly SSL setup that creates self-signed certificates
 * using the existing CertificateManager
 */
object WindowsSSLSetup {
  
  def createSelfSignedCertificate(
    domain: String = "127.0.0.1",
    port: Int = 8443
  ): Task[SSLSetup] = {
    for {
      // Use the existing CertificateManager to create certificate
      cert <- CertificateManager.ensureSelfSignedCertificate(
        keystorePath = "schwab-oauth.p12",
        password = "password",
        hostname = domain,
        port = port
      )
      
      // Load the keystore
      keyStore <- ZIO.attempt {
        val ks = KeyStore.getInstance("PKCS12")
        val fis = new java.io.FileInputStream("schwab-oauth.p12")
        ks.load(fis, "password".toCharArray)
        fis.close()
        ks
      }
      
      // Create SSL context
      sslContext <- ZIO.attempt {
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
        kmf.init(keyStore, "password".toCharArray)
        
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
        tmf.init(keyStore)
        
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(kmf.getKeyManagers, createTrustAllManager(), new SecureRandom())
        ctx
      }
      
      // Create SSL config using the keystore file
      sslConfig = SSLConfig.fromJavaxNetSslKeyStoreFile(
        "schwab-oauth.p12",
        Some(Config.Secret("password")),
        keyManagerKeyStoreType = "PKCS12"
      )
      
    } yield SSLSetup(
      config = sslConfig,
      certificate = cert
    )
  }
  
  // Trust manager that accepts self-signed certificates for localhost
  private def createTrustAllManager(): Array[TrustManager] = {
    Array(new X509TrustManager {
      def getAcceptedIssuers: Array[X509Certificate] = Array.empty
      def checkClientTrusted(certs: Array[X509Certificate], authType: String): Unit = {}
      def checkServerTrusted(certs: Array[X509Certificate], authType: String): Unit = {
        // Only trust localhost/127.0.0.1 certificates
        val cn = certs(0).getSubjectX500Principal.getName
        if (!cn.contains("127.0.0.1") && !cn.contains("localhost")) {
          throw new java.security.cert.CertificateException("Certificate not for localhost")
        }
      }
    })
  }
}