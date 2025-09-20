-- NSE Stock Analysis Bot Database Initialization Script
-- This script creates the initial database structure and default data

-- Create database (already created by Docker environment variables)
-- DATABASE: nse_bot (production) / nse_bot_dev (development)

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- Create indexes for better performance on common queries
-- These will be created automatically by JPA/Hibernate, but we can add custom ones here

-- Custom index for user telegram_id lookups (most common query)
-- CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_telegram_id ON users(telegram_id);

-- Custom index for portfolio stock symbol lookups
-- CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_portfolio_symbol ON portfolio(symbol);

-- Custom index for user daily request tracking
-- CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_requests ON users(daily_requests_made, daily_request_reset_time);

-- Insert default system configuration if needed
-- (This will be handled by the application code)

-- Create a function to update the updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- This trigger will be applied to tables that have updated_at columns
-- The actual table creation is handled by JPA/Hibernate migrations

-- Set default timezone
SET timezone = 'Asia/Kolkata';

-- Log successful initialization
DO $$
BEGIN
    RAISE NOTICE 'NSE Bot database initialized successfully at %', NOW();
END $$;