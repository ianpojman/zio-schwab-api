# Schwab API Notes

## Token Refresh Synchronization Fix (2025-08-05)

Fixed an issue where token refresh failures would cause cascading OAuth authentication attempts:
- Added synchronization using Semaphore and Ref to prevent concurrent OAuth flows
- Added separate synchronization for token refresh operations  
- Added SSL certificate caching to avoid concurrent certificate generation issues
- The fix ensures only one authentication flow or token refresh happens at a time

## Price History Frequency Constraints

The Schwab API has specific constraints on the frequency parameter when requesting price history data:

### Valid Minute Frequencies
When using `frequencyType = "minute"`, the following frequencies are valid:
- 1 minute
- 5 minutes  
- 10 minutes
- 15 minutes
- 30 minutes

**Note**: 60-minute (hourly) data is NOT supported with `frequencyType = "minute"`. 

### Getting Hourly Data
To get hourly data, you should use:
- `frequencyType = "daily"` with appropriate period settings
- Or aggregate lower frequency data (e.g., 30-minute bars)

### Error Example
Requesting 60-minute frequency will result in:
```
API error: BadRequest - "When frequencyType=minute valid values for frequency are: [1, 5, 10, 15, 30]"
```

## Recommended Approach for Specula

For applications requiring 60-minute bars:
1. Use Alpaca as the primary market data provider (supports flexible intervals)
2. Use Schwab for real-time quotes and order management
3. Consider the hybrid approach: Schwab for real-time, Alpaca for historical