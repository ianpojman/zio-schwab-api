package net.ipojman.schwab.zio

/**
 * Extracts the authorization code from a callback URL
 * Based on the Python implementation that handles the specific URL format
 * from Schwab API
 */
object AuthCodeExtractor {
  /**
   * Extracts the authorization code from a callback URL
   * Example URL: https://127.0.0.1/?code=C0.b2F1dGgyLmJkYy5zY2h3YWIuY29t.lWYW0llWEhywO5npPZSfwcPnc8fHtXlIwUvkJvMgrU0%40&session=f5931878-7ee0-423d-817e-b8f55b6e9672
   *
   * @param url The full callback URL
   * @return The authorization code
   */
  def extractCode(url: String): String = {
    if (url.startsWith("https://") && url.contains("code=")) {
      // This closely matches the Python implementation's approach
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
    } else if (url.contains("code=")) {
      // Just the code portion
      val codeStart = url.indexOf("code=") + 5
      val codeEnd = url.indexOf("%40", codeStart)

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
