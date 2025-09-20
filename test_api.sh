#!/bin/bash

echo "=== Testing NSE Direct API Call ==="
curl -H "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36" \
     -H "Accept: application/json, text/plain, */*" \
     "https://www.nseindia.com/api/quote-equity?symbol=RELIANCE" \
     --max-time 10 --silent --show-error || echo "NSE API Failed"

echo -e "\n\n=== Testing Yahoo Finance Direct API Call ==="
curl "https://query1.finance.yahoo.com/v8/finance/chart/RELIANCE.NS" \
     --max-time 10 --silent --show-error || echo "Yahoo Finance API Failed"

echo -e "\n\nDirect API test completed"