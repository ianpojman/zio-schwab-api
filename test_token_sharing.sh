#!/bin/bash
echo "Running first example..."
sbt "runMain net.ipojman.schwab.zio.OrderHistoryExample" 2>&1 | grep -E "(Token|Refreshing|Using existing)"
echo -e "\nWaiting 2 seconds...\n"
sleep 2
echo "Running second example..."
sbt "runMain net.ipojman.schwab.zio.MarketDataExample" 2>&1 | grep -E "(Token|Refreshing|Using existing)"