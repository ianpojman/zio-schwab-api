package net.ipojman.schwab.zio

import zio.*
import zio.json.*

object SimpleTokenCheck extends ZIOAppDefault {
  
  override def run = {
    for {
      _ <- Console.printLine("=== Simple Token Check ===")
      
      // Our library stores tokens wrapped in StoredToken
      storage = new FileTokenStorage()
      tokenOpt <- storage.getToken()
      
      _ <- tokenOpt match {
        case Some(token) =>
          for {
            _ <- Console.printLine(s"\n✓ Token found via FileTokenStorage")
            _ <- Console.printLine("  Access token: present (not shown)")
            _ <- Console.printLine(s"  Has refresh token: ${token.refresh_token.isDefined}")
            
            // The other app might expect just the TokenResponse, not wrapped
            // Let's create a file with just the token
            legacyFormat = token.toJson
            _ <- Console.printLine(s"\nToken in legacy format: ${legacyFormat.length} characters (not shown)")
            
          } yield ()
        case None =>
          Console.printLine("\n✗ No token found")
      }
      
    } yield ()
  }
}