# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a ZIO-based Scala client library for the Schwab Trading API. It provides type-safe, functional interfaces to Schwab's REST API endpoints for trading, market data, and account management.

## Build and Development Commands

```bash
# Compile the project
sbt compile

# Run tests
sbt test

# Run a specific test
sbt "testOnly *SpecificTestClass"

# Launch SBT interactive shell
sbt

# Run example applications
sbt "runMain net.ipojman.schwab.zio.MarketDataExample"
sbt "runMain net.ipojman.schwab.zio.ApiExampleWithServer"
sbt "runMain net.ipojman.schwab.zio.ClientCredentialsExample"

# Clean and rebuild
sbt clean compile
```

## Architecture

### Core Components

1. **SchwabClient** (`SchwabApi.scala`) - Main API client interface with two implementations:
   - `LiveSchwabClient` - Basic implementation requiring manual OAuth handling
   - `SeamlessSchwabClient` - Recommended implementation with automatic OAuth flow and local HTTPS server

2. **Service Layers** - Domain-specific service interfaces:
   - `MarketDataService` - Market quotes, option chains, price history
   - `OrderService` - Order placement, modification, and retrieval
   - Account services (planned)

3. **OAuth Authentication** - Multiple OAuth flows supported:
   - Authorization Code flow with local HTTPS callback server
   - Client Credentials flow
   - Refresh token flow

4. **Certificate Management** (`cert/` package) - SSL certificate handling for local HTTPS server:
   - `MkcertManager` - Uses mkcert for local trusted certificates
   - `WindowsSSLSetup` - Windows-specific SSL configuration

### Key Design Patterns

- All API calls return ZIO effects for composability and error handling
- Service interfaces use companion objects with accessor methods
- Configuration via HOCON files or environment variables
- Models use case classes with JSON codecs (zio-json)

## Configuration

The library expects configuration in `~/.schwab.conf` or via environment variables:

```hocon
clientId = "YOUR_CLIENT_ID"
clientSecret = "YOUR_CLIENT_SECRET"
redirectUri = "https://127.0.0.1:8443/api/oauth/callback"
```

## OAuth Flow Considerations

- Windows may require additional time for server startup (see WINDOWS_AUTH_FIX.md)
- Local HTTPS server uses port 8443 by default
- Browser auto-opens for authorization in seamless client

## Testing

Uses ZIO Test framework. Run with:
```bash
sbt test
```

## Dependencies

- Scala 3.6.4
- ZIO 2.1.16 ecosystem (zio-http, zio-json, zio-config)
- SSL: BouncyCastle, ACME4J
- Testing: specs2, zio-test