-- Add additional User entity fields to users table
-- This migration adds fields needed by the UserDetails implementation

-- Add account status fields
ALTER TABLE users 
ADD COLUMN IF NOT EXISTS account_non_expired BOOLEAN DEFAULT TRUE,
ADD COLUMN IF NOT EXISTS account_non_locked BOOLEAN DEFAULT TRUE,
ADD COLUMN IF NOT EXISTS credentials_non_expired BOOLEAN DEFAULT TRUE,
ADD COLUMN IF NOT EXISTS enabled BOOLEAN DEFAULT TRUE;

-- Add indexes for performance
CREATE INDEX IF NOT EXISTS idx_users_enabled ON users(enabled);
CREATE INDEX IF NOT EXISTS idx_users_account_non_locked ON users(account_non_locked);

-- Add comments
COMMENT ON COLUMN users.account_non_expired IS 'Account expiration status for Spring Security';
COMMENT ON COLUMN users.account_non_locked IS 'Account lock status for Spring Security';
COMMENT ON COLUMN users.credentials_non_expired IS 'Credentials expiration status for Spring Security';
COMMENT ON COLUMN users.enabled IS 'Account enabled status for Spring Security';