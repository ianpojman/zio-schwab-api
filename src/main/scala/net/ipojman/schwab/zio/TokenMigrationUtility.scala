package net.ipojman.schwab.zio

import zio.*
import zio.json.*
import java.nio.file.{Files, Paths, StandardCopyOption}

/**
 * Utility to help check token format and status
 * Since we're no longer supporting legacy format, this just checks the current token
 */
object TokenMigrationUtility extends ZIOAppDefault {
  
  override def run = {
    for {
      _ <- Console.printLine("=== Token Status Check ===")
      
      homePath <- ZIO.succeed(java.lang.System.getProperty("user.home"))
      tokenPath = s"$homePath/.schwab_token.json"
      
      // Read current token file
      exists <- ZIO.attempt(Files.exists(Paths.get(tokenPath))).orDie
      
      _ <- if (exists) {
        for {
          content <- ZIO.attempt(new String(Files.readAllBytes(Paths.get(tokenPath)))).orDie
          
          // Try to parse as StoredToken (new format)
          _ <- content.fromJson[StoredToken] match {
            case Right(stored) =>
              val currentTime = java.lang.System.currentTimeMillis() / 1000
              val age = currentTime - stored.obtainedAt
              val expires = stored.token.expires_in.getOrElse(1800)
              val remaining = expires - age.toInt
              val isValid = remaining > 300 // 5 minute buffer
              
              Console.printLine("\n✅ Token file is in NEW format with timestamp") *>
              Console.printLine(s"Token age: ${age}s") *>
              Console.printLine(s"Expires in: ${expires}s") *>
              Console.printLine(s"Time remaining: ${remaining}s") *>
              Console.printLine(s"Valid: $isValid") *>
              Console.printLine(s"Has refresh token: ${stored.token.refresh_token.isDefined}")
              
            case Left(_) =>
              // Try legacy format
              content.fromJson[TokenResponse] match {
                case Right(token) =>
                  Console.printLine("\n⚠️  Token file is in LEGACY format (no timestamp)") *>
                  Console.printLine("This format is no longer supported.") *>
                  Console.printLine("The token will be treated as expired and re-authenticated on next use.") *>
                  Console.printLine(s"Has refresh token: ${token.refresh_token.isDefined}")
                  
                case Left(err) =>
                  Console.printLine(s"\n❌ Token file exists but cannot be parsed: $err") *>
                  Console.printLine("File will be ignored and new authentication will be required.")
              }
          }
          
        } yield ()
      } else {
        Console.printLine(s"\n❌ No token file found at: $tokenPath")
      }
      
    } yield ()
  }
}