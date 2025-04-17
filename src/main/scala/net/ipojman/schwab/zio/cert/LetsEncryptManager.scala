package net.ipojman.schwab.zio.cert

import org.shredzone.acme4j._
import org.shredzone.acme4j.challenge._
import org.shredzone.acme4j.util._
import org.shredzone.acme4j.exception._

import java.io.{File, FileReader, FileWriter}
import java.security.KeyPair

object LetsEncryptManager {
//  case class ACMESetup(
//                        config: Server.Config,
//                        certificate: java.security.cert.X509Certificate
//                      )
//
//  def obtainCertificate(
//                         domain: String,
//                         email: String,
//                         keyStorePassword: String,
//                         useStaging: Boolean = true // Set to false for production
//                       ): ZIO[Any, Throwable, ACMESetup] = {
//    ZIO.attempt {
//      // 1. Create or load account key
//      val accountKeyFile = new File("account.key")
//      val accountKeyPair = if (accountKeyFile.exists()) {
//        KeyPairUtils.readKeyPair(new FileReader(accountKeyFile))
//      } else {
//        val keyPair = KeyPairUtils.createKeyPair(2048)
//        KeyPairUtils.writeKeyPair(keyPair, new FileWriter(accountKeyFile))
//        keyPair
//      }
//
//      // 2. Create or load domain key
//      val domainKeyFile = new File(s"$domain.key")
//      val domainKeyPair = if (domainKeyFile.exists()) {
//        KeyPairUtils.readKeyPair(new FileReader(domainKeyFile))
//      } else {
//        val keyPair = KeyPairUtils.createKeyPair(2048)
//        KeyPairUtils.writeKeyPair(keyPair, new FileWriter(domainKeyFile))
//        keyPair
//      }
//
//      // 3. Connect to Let's Encrypt server
//      val acmeServerUrl = if (useStaging) {
//        "acme://letsencrypt.org/staging"
//      } else {
//        "acme://letsencrypt.org"
//      }
//
//      val session = new Session(acmeServerUrl)
//      val account = new AccountBuilder()
//        .addContact("mailto:" + email)
//        .agreeToTermsOfService()
//        .useKeyPair(accountKeyPair)
//        .create(session)
//
//      // 4. Order certificate
//      val order = account.newOrder()
//        .domains(domain)
//        .create()
//
//      // 5. Handle HTTP challenge
//      val auth = order.getAuthorizations().get(0)
//      val challenge = auth.findChallenge(Http01Challenge.TYPE)
//        .orElseThrow(() => new AcmeException("No HTTP challenge found"))
//      val http01Challenge = challenge.asInstanceOf[Http01Challenge]
//
//      val token = http01Challenge.getToken()
//      val content = http01Challenge.getAuthorization()
//
//      // This token and content need to be served at:
//      // http://$domain/.well-known/acme-challenge/$token
//      // We'll handle this in the HTTP routes
//
//      // Trigger challenge
//      challenge.trigger()
//
//      // Wait for challenge to complete
//      var status = challenge.getStatus()
//      while (status != Status.VALID && status != Status.INVALID) {
//        Thread.sleep(3000)
//        challenge.update()
//        status = challenge.getStatus()
//      }
//
//      if (status != Status.VALID) {
//        throw new AcmeException("Challenge failed")
//      }
//
//      // 6. Finalize order
//      val csr = new CSRBuilder()
//      csr.addDomain(domain)
//      csr.sign(domainKeyPair)
//      val csrBytes = csr.getEncoded()
//
//      order.execute(csrBytes)
//
//      // Wait for order to complete
//      var orderStatus = order.getStatus()
//      while (orderStatus != Status.VALID && orderStatus != Status.INVALID) {
//        Thread.sleep(3000)
//        order.update()
//        orderStatus = order.getStatus()
//      }
//
//      if (orderStatus != Status.VALID) {
//        throw new AcmeException("Order failed")
//      }
//
//      // 7. Download certificate chain
//      val cert = order.getCertificate()
//      val certChain = cert.getCertificateChain()
//
//      // 8. Store in keystore for use with ZIO HTTP
//      val keyStore = KeyStore.getInstance("PKCS12")
//      keyStore.load(null, null)
//      keyStore.setKeyEntry(
//        "letsencrypt",
//        domainKeyPair.getPrivate,
//        keyStorePassword.toCharArray,
//        certChain
//      )
//
//      val keyStoreFile = new File(s"$domain.p12")
//      val out = new FileOutputStream(keyStoreFile)
//      keyStore.store(out, keyStorePassword.toCharArray)
//      out.close()
//
//      // 9. Create SSL config
//      val sslConfig = Server.Config.default
//        .port(443)
//        .address("0.0.0.0")
//        .ssl(
//          SSLConfig.fromJavaxNetSslKeyStoreFile(
//            keyStoreFile.getPath,
//            Some(Config.Secret(keyStorePassword)),
//            keyManagerKeyStoreType = "PKCS12"
//          )
//        )
//
//      ACMESetup(sslConfig, certChain(0))
//    }
//  }
}