package net.ipojman.schwab.zio

import zio.*
import zio.json.*
import java.nio.file.{Files, Paths, StandardCopyOption}

/**
 * Utility to help migrate between token formats
 */
object TokenMigrationUtility extends ZIOAppDefault {
  
  override def run = {
    for {
      _ <- Console.printLine("=== Token Migration Utility ===")
      
      homePath <- ZIO.succeed(java.lang.System.getProperty("user.home"))
      newFormatPath = s"$homePath/.schwab_token.json"
      legacyFormatPath = s"$homePath/.schwab_token_legacy.json"
      
      // Read current token file
      exists <- ZIO.attempt(Files.exists(Paths.get(newFormatPath))).orDie
      
      _ <- if (exists) {
        for {
          content <- ZIO.attempt(new String(Files.readAllBytes(Paths.get(newFormatPath)))).orDie
          _ <- Console.printLine(s"\nCurrent token file content (first 200 chars):\n${content.take(200)}...")
          
          // Extract just the token part for legacy compatibility
          storage = new FileTokenStorage()
          tokenOpt <- storage.getToken()
          
          _ <- tokenOpt match {
            case Some(token) =>
              for {
                // Write legacy format (just the token, no wrapper)
                legacyJson <- ZIO.succeed(token.toJson)
                _ <- ZIO.attempt {
                  Files.write(Paths.get(legacyFormatPath), legacyJson.getBytes("UTF-8"))
                }.orDie
                _ <- Console.printLine(s"\n✓ Created legacy format token at: $legacyFormatPath")
                
                // Also create a backup of the new format
                backupPath = s"$homePath/.schwab_token_backup.json"
                _ <- ZIO.attempt {
                  Files.copy(Paths.get(newFormatPath), Paths.get(backupPath), StandardCopyOption.REPLACE_EXISTING)
                }.orDie
                _ <- Console.printLine(s"✓ Backed up new format token to: $backupPath")
                
                // Now overwrite the main token file with legacy format
                _ <- ZIO.attempt {
                  Files.write(Paths.get(newFormatPath), legacyJson.getBytes("UTF-8"))
                }.orDie
                _ <- Console.printLine(s"✓ Converted main token file to legacy format")
                
                _ <- Console.printLine("\nThe token file has been converted to legacy format.")
                _ <- Console.printLine("Your other application should now be able to read it.")
                _ <- Console.printLine(s"The original format is backed up at: $backupPath")
                
              } yield ()
            case None =>
              Console.printLine("\n✗ Could not read token from file")
          }
        } yield ()
      } else {
        Console.printLine(s"\n✗ No token file found at: $newFormatPath")
      }
      
    } yield ()
  }
}