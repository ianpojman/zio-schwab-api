package net.ipojman.schwab.zio.cert

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.{BasicConstraints, Extension, GeneralName, GeneralNames}
import org.bouncycastle.cert.jcajce.{JcaX509CertificateConverter, JcaX509v3CertificateBuilder}
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import zio.{Config, Task, ZIO}
import zio.http.{SSLConfig, Server}

import java.io.{File, FileOutputStream}
import java.lang.System.currentTimeMillis
import java.math.BigInteger
import java.security.cert.X509Certificate
import java.security.{KeyPair, KeyPairGenerator, KeyStore}
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

case class SSLSetup(
                     config: zio.http.SSLConfig,
                     certificate: java.security.cert.X509Certificate
                   )

object CertificateManager {
  /**
   * Generate a self-signed certificate and keystore
   */
  def ensureSelfSignedCertificate(
                                   keystorePath: String = "keystore.p12",
                                   password: String = "password",
                                   hostname: String = "127.0.0.1",
                                   port: Int = 8443
                                 ): Task[X509Certificate] = {
    ZIO.attempt {
      val keystoreFile = new File(keystorePath)

//      // Skip if keystore already exists
//      if (keystoreFile.exists()) {
//        // read from file 
//        
//      }

      // Generate key pair
      val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
      keyPairGenerator.initialize(2048)
      val keyPair = keyPairGenerator.generateKeyPair()

      // Create certificate
      val certificate = generateCertificate(keyPair, hostname)

      // Store in keystore
      val keyStore = KeyStore.getInstance("PKCS12")
      keyStore.load(null, null)
      keyStore.setKeyEntry("server", keyPair.getPrivate, password.toCharArray, Array(certificate))

      val out = new FileOutputStream(keystorePath)
      keyStore.store(out, password.toCharArray)
      out.close()
      
      certificate
    }
  }

  /**
   * Get SSL config for ZIO HTTP server
   */
  def getSslConfig(
                    port: Int = 8443,
                    keystorePath: String = "keystore.p12",
                    password: String = "password"
                  ): ZIO[Any, Throwable, SSLSetup] = {
    for {
      certificate <- ensureSelfSignedCertificate(keystorePath, password, "127.0.0.1", port)
    } yield SSLSetup(
          SSLConfig.fromJavaxNetSslKeyStoreFile(
            keystorePath,
            Some(Config.Secret(password)),
            keyManagerKeyStoreType = "PKCS12"
        ),
      certificate
    )
  }
  private def generateCertificate(keyPair: KeyPair, hostname: String) = {
    // Certificate information
    val issuer = new X500Name(s"CN=$hostname, O=Development, OU=ZIO Schwab API")
    val subject = issuer

    // Validity period (1 year)
    val now = Instant.now()
    val validFrom = Date.from(now)
    val validTo = Date.from(now.plus(365, ChronoUnit.DAYS))

    // Serial number
    val serialNumber = BigInteger.valueOf(currentTimeMillis())

    // Certificate builder
    val builder = new JcaX509v3CertificateBuilder(
      issuer,
      serialNumber,
      validFrom,
      validTo,
      subject,
      keyPair.getPublic
    )

    // Add extensions - DNS name and BasicConstraints
    builder.addExtension(
      Extension.subjectAlternativeName,
      false,
      new GeneralNames(new GeneralName(GeneralName.dNSName, hostname))
    )

    builder.addExtension(
      Extension.basicConstraints,
      true,
      new BasicConstraints(false)
    )

    // Sign the certificate
    val signer = new JcaContentSignerBuilder("SHA256withRSA")
      .setProvider("BC")
      .build(keyPair.getPrivate)

    // Generate the certificate
    val holder = builder.build(signer)

    // Convert to JCA certificate
    new JcaX509CertificateConverter()
      .setProvider("BC")
      .getCertificate(holder)
  }
}
