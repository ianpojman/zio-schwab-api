# Windows OAuth Server Fix

## Problem
On Windows, the OAuth callback server was starting asynchronously but the browser was opening immediately, leading to ERR_CONNECTION_REFUSED errors. The issue was that the server needed time to bind to the port before the browser attempted to connect.

## Solution
Two approaches have been implemented:

### 1. Promise-based Server Readiness (SeamlessSchwabClient.scala)
- Uses a `Promise[Throwable, Unit]` to signal when the server is ready
- Tests server connectivity before opening the browser
- Retries connection test up to 5 times with 500ms spacing
- Only opens browser after confirming server is listening

### 2. Simple Delay Approach (SeamlessSchwabClientSimple.scala)
- Uses a platform-specific delay (3 seconds on Windows, 1 second on others)
- Provides better error messages if server fails to start
- Fallback to manual URL if browser fails to open

## Key Changes

### In `initiateOAuthFlow()`:
```scala
// Before (problematic):
serverFiber <- startAuthServer(authCompleted).fork
_ <- BrowserLauncher.openBrowser(authUrl)  // Opens immediately!

// After (fixed):
serverReady <- Promise.make[Throwable, Unit]
serverFiber <- startAuthServer(authCompleted, serverReady).fork
_ <- serverReady.await  // Wait for server to be ready
_ <- BrowserLauncher.openBrowser(authUrl)
```

### In `startAuthServer()`:
```scala
// Test server connection before signaling ready
_ <- testServerConnection(port).retry(Schedule.recurs(5) && Schedule.spaced(500.millis))
  .tapBoth(
    err => serverReady.fail(err),
    _ => serverReady.succeed(())
  )
```

## Testing
Run `TestServerReadiness` to verify the fix:
```bash
sbt "runMain net.specula.v3.portfolio.service.TestServerReadiness"
```

## Windows-Specific Considerations
1. Windows may need more time for server startup
2. Firewall may block the port - add exception if needed
3. Use HTTP (port 8080) instead of HTTPS if SSL issues persist
4. May need to run as Administrator for port binding