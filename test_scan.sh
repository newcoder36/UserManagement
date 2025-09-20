#!/bin/bash

echo "Testing enhanced scan functionality..."
echo "Making API call to scan endpoint..."

curl -X GET "http://localhost:8082/api/scan" \
  -H "Content-Type: application/json" \
  -v

echo ""
echo "Scan test completed."