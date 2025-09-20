#!/bin/bash

echo "================================================="
echo "  NSE Stock Analysis Bot - Starting..."
echo "================================================="
echo

# Check if environment variables are set
if [ -z "$TELEGRAM_BOT_TOKEN" ]; then
    echo "ERROR: TELEGRAM_BOT_TOKEN environment variable is not set!"
    echo
    echo "Please set your bot token:"
    echo "export TELEGRAM_BOT_TOKEN=your_bot_token_here"
    echo
    echo "Or create application-local.yml with your configuration"
    echo
    exit 1
fi

echo "Bot Token: ${TELEGRAM_BOT_TOKEN:0:10}..."
echo
echo "Building and starting the application..."
echo

mvn spring-boot:run -Dspring-boot.run.profiles=local