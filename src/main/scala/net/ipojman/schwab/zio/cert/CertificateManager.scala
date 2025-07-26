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
      println(s"Creating SSL certificate for $hostname:$port at $keystorePath")
      val keystoreFile = new File(keystorePath)

//      // Skip if keystore already exists
//      if (keystoreFile.exists()) {
//        // read from file 
//        
//      }

      // Generate key pair
      println("Generating RSA key pair...")
      val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
      keyPairGenerator.initialize(2048)
      val keyPair = keyPairGenerator.generateKeyPair()
      println("Key pair generated successfully")

      // Create certificate
      println("Generating self-signed certificate...")
      val certificate = generateCertificate(keyPair, hostname)
      println("Certificate generated successfully")

      // Store in keystore
      println("Creating PKCS12 keystore...")
      val keyStore = KeyStore.getInstance("PKCS12")
      keyStore.load(null, null)
      keyStore.setKeyEntry("server", keyPair.getPrivate, password.toCharArray, Array(certificate))

      println(s"Saving keystore to $keystorePath...")
      val out = new FileOutputStream(keystorePath)
      keyStore.store(out, password.toCharArray)
      out.close()
      println("Keystore saved successfully")
      
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
    try {
      println("Setting up certificate parameters...")
      // Certificate information
      val issuer = new X500Name(s"CN=$hostname, O=Development, OU=ZIO Schwab API")
      val subject = issuer

      // Validity period (1 year)
      val now = Instant.now()
      val validFrom = Date.from(now)
      val validTo = Date.from(now.plus(365, ChronoUnit.DAYS))
      println(s"Certificate validity: $validFrom to $validTo")

      // Serial number
      val serialNumber = BigInteger.valueOf(currentTimeMillis())

      // Certificate builder
      println("Creating certificate builder...")
      val builder = new JcaX509v3CertificateBuilder(
        issuer,
        serialNumber,
        validFrom,
        validTo,
        subject,
        keyPair.getPublic
      )

      // Add extensions - DNS name and BasicConstraints
      println("Adding certificate extensions...")
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

      // Check if BouncyCastle provider is available
      println("Checking BouncyCastle provider...")
      val bcProvider = java.security.Security.getProvider("BC")
      if (bcProvider == null) {
        println("BouncyCastle provider not found, adding it...")
        java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider())
      } else {
        println("BouncyCastle provider is available")
      }

      // Sign the certificate
      println("Creating content signer...")
      val signer = new JcaContentSignerBuilder("SHA256withRSA")
        .setProvider("BC")
        .build(keyPair.getPrivate)

      // Generate the certificate
      println("Building certificate...")
      val holder = builder.build(signer)

      // Convert to JCA certificate
      println("Converting to JCA certificate...")
      val certificate = new JcaX509CertificateConverter()
        .setProvider("BC")
        .getCertificate(holder)
      
      println("Certificate generation completed successfully")
      certificate
    } catch {
      case e: Exception =>
        println(s"Certificate generation failed: ${e.getMessage}")
        e.printStackTrace()
        throw e
    }
  }
}
