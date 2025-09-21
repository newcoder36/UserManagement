-- Migration V8: Add Missing Tables for Entity Alignment
-- Creates missing tables and fields to fully align with Java entities

-- 1. Add missing columns to users table for UserDetails implementation
-- These fields are required by the User entity for Spring Security integration
ALTER TABLE users 
ADD COLUMN IF NOT EXISTS enabled BOOLEAN DEFAULT TRUE,
ADD COLUMN IF NOT EXISTS account_non_expired BOOLEAN DEFAULT TRUE,
ADD COLUMN IF NOT EXISTS account_non_locked BOOLEAN DEFAULT TRUE,
ADD COLUMN IF NOT EXISTS credentials_non_expired BOOLEAN DEFAULT TRUE;

-- Create indexes for UserDetails fields
CREATE INDEX IF NOT EXISTS idx_users_enabled ON users(enabled);
CREATE INDEX IF NOT EXISTS idx_users_account_non_expired ON users(account_non_expired);
CREATE INDEX IF NOT EXISTS idx_users_account_non_locked ON users(account_non_locked);
CREATE INDEX IF NOT EXISTS idx_users_credentials_non_expired ON users(credentials_non_expired);

-- 2. Ensure MFA configuration matches entity exactly
-- Add missing columns that might not exist
ALTER TABLE mfa_configuration 
ADD COLUMN IF NOT EXISTS mfa_type VARCHAR(20) NOT NULL DEFAULT 'TOTP';

-- Update constraint to match entity enum values
ALTER TABLE mfa_configuration DROP CONSTRAINT IF EXISTS mfa_configuration_mfa_type_check;
ALTER TABLE mfa_configuration ADD CONSTRAINT mfa_configuration_mfa_type_check 
    CHECK (mfa_type IN ('SMS', 'EMAIL', 'TOTP', 'BIOMETRIC'));

-- 3. Ensure user_profiles table matches UserProfile entity completely
-- Add missing columns for behavioral settings
ALTER TABLE user_profiles 
ADD COLUMN IF NOT EXISTS address VARCHAR(500),
ADD COLUMN IF NOT EXISTS timezone VARCHAR(50) DEFAULT 'UTC';

-- Update existing records with default timezone
UPDATE user_profiles SET timezone = 'UTC' WHERE timezone IS NULL;

-- 4. Add missing columns to user_devices for UserDevice entity alignment
ALTER TABLE user_devices 
ADD COLUMN IF NOT EXISTS trust_expiry TIMESTAMP;

-- Update column names to match entity
-- Rename columns if they have different names
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'user_devices' AND column_name = 'trusted') THEN
        -- Column already exists, ensure it matches
        NULL;
    ELSIF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'user_devices' AND column_name = 'is_trusted') THEN
        ALTER TABLE user_devices RENAME COLUMN is_trusted TO trusted;
    ELSE
        ALTER TABLE user_devices ADD COLUMN trusted BOOLEAN DEFAULT FALSE;
    END IF;
END$$;

-- Rename first_seen to firstSeen equivalent if needed
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'user_devices' AND column_name = 'first_seen') THEN
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'user_devices' AND column_name = 'first_seen_at') THEN
            ALTER TABLE user_devices RENAME COLUMN first_seen_at TO first_seen;
        END IF;
    END IF;
END$$;

-- Rename last_seen to lastSeen equivalent if needed
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'user_devices' AND column_name = 'last_seen') THEN
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'user_devices' AND column_name = 'last_seen_at') THEN
            ALTER TABLE user_devices RENAME COLUMN last_seen_at TO last_seen;
        END IF;
    END IF;
END$$;

-- 5. Ensure auth_audit_log matches AuthAuditLog entity
-- Add missing columns and indexes
CREATE INDEX IF NOT EXISTS idx_auth_audit_log_blockchain_hash ON auth_audit_log(blockchain_hash);
CREATE INDEX IF NOT EXISTS idx_auth_audit_log_previous_hash ON auth_audit_log(previous_hash);

-- 6. Update verification_tokens to match VerificationToken entity exactly
-- Ensure the token_type constraint matches the enum
ALTER TABLE verification_tokens DROP CONSTRAINT IF EXISTS verification_tokens_token_type_check;
ALTER TABLE verification_tokens ADD CONSTRAINT verification_tokens_token_type_check 
    CHECK (token_type IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET', 'MFA_BACKUP'));

-- Add index on user_agent if missing
CREATE INDEX IF NOT EXISTS idx_verification_tokens_user_agent ON verification_tokens(user_agent);

-- 7. Add cascade relationships for user_role_assignments to match UserRoleAssignment entity
-- Ensure proper cascade behavior
ALTER TABLE user_role_assignments DROP CONSTRAINT IF EXISTS user_role_assignments_user_id_fkey;
ALTER TABLE user_role_assignments DROP CONSTRAINT IF EXISTS user_role_assignments_role_id_fkey;
ALTER TABLE user_role_assignments ADD CONSTRAINT user_role_assignments_user_id_fkey 
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE user_role_assignments ADD CONSTRAINT user_role_assignments_role_id_fkey 
    FOREIGN KEY (role_id) REFERENCES user_roles(id) ON DELETE CASCADE;

-- 8. Create proper indexes for all entity relationships
CREATE INDEX IF NOT EXISTS idx_user_profile_user_id_unique ON user_profiles(user_id);
CREATE INDEX IF NOT EXISTS idx_verification_tokens_expires_at ON verification_tokens(expires_at);
CREATE INDEX IF NOT EXISTS idx_verification_tokens_created_at ON verification_tokens(created_at);

-- 9. Update data types to ensure compatibility
-- Ensure InetAddress fields are properly typed
ALTER TABLE users ALTER COLUMN last_login_ip TYPE INET USING last_login_ip::INET;
ALTER TABLE user_devices ALTER COLUMN ip_address TYPE INET USING ip_address::INET;
ALTER TABLE user_sessions ALTER COLUMN ip_address TYPE INET USING ip_address::INET;

-- 10. Add proper comments for all entity alignments
COMMENT ON COLUMN users.enabled IS 'Spring Security UserDetails enabled field';
COMMENT ON COLUMN users.account_non_expired IS 'Spring Security UserDetails account expiration field';
COMMENT ON COLUMN users.account_non_locked IS 'Spring Security UserDetails account lock field';
COMMENT ON COLUMN users.credentials_non_expired IS 'Spring Security UserDetails credentials expiration field';

COMMENT ON COLUMN user_devices.trusted IS 'Device trust status matching UserDevice entity';
COMMENT ON COLUMN user_devices.first_seen IS 'First device usage timestamp matching UserDevice entity';
COMMENT ON COLUMN user_devices.last_seen IS 'Last device usage timestamp matching UserDevice entity';
COMMENT ON COLUMN user_devices.trust_expiry IS 'Device trust expiration timestamp matching UserDevice entity';

COMMENT ON COLUMN user_profiles.address IS 'User address for KYC compliance matching UserProfile entity';
COMMENT ON COLUMN user_profiles.timezone IS 'User timezone preference matching UserProfile entity';

-- 11. Validation and constraint updates
-- Ensure NOT NULL constraints where required by entities
-- Note: Only add these after ensuring data integrity in production

-- Update existing NULL values before adding constraints
UPDATE users SET enabled = TRUE WHERE enabled IS NULL;
UPDATE users SET account_non_expired = TRUE WHERE account_non_expired IS NULL;
UPDATE users SET account_non_locked = TRUE WHERE account_non_locked IS NULL;
UPDATE users SET credentials_non_expired = TRUE WHERE credentials_non_expired IS NULL;

-- Add NOT NULL constraints for required fields
ALTER TABLE users ALTER COLUMN enabled SET NOT NULL;
ALTER TABLE users ALTER COLUMN account_non_expired SET NOT NULL;
ALTER TABLE users ALTER COLUMN account_non_locked SET NOT NULL;
ALTER TABLE users ALTER COLUMN credentials_non_expired SET NOT NULL;

-- 12. Create composite indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_users_security_status ON users(enabled, account_non_locked, account_non_expired, credentials_non_expired);
CREATE INDEX IF NOT EXISTS idx_user_devices_user_trusted ON user_devices(user_id, trusted);
CREATE INDEX IF NOT EXISTS idx_mfa_configuration_user_enabled ON mfa_configuration(user_id, enabled);
CREATE INDEX IF NOT EXISTS idx_user_sessions_user_active ON user_sessions(user_id, active);

-- Final validation: Ensure all foreign key relationships are consistent
-- This helps prevent runtime errors from entity relationship mismatches
DO $$
DECLARE
    rec RECORD;
BEGIN
    -- Validate mfa_configuration user_id references
    FOR rec IN SELECT user_id FROM mfa_configuration WHERE user_id NOT IN (SELECT id FROM users)
    LOOP
        DELETE FROM mfa_configuration WHERE user_id = rec.user_id;
        RAISE NOTICE 'Removed orphaned mfa_configuration record for user_id: %', rec.user_id;
    END LOOP;
    
    -- Validate other foreign key references similarly
    FOR rec IN SELECT user_id FROM user_devices WHERE user_id NOT IN (SELECT id FROM users)
    LOOP
        DELETE FROM user_devices WHERE user_id = rec.user_id;
        RAISE NOTICE 'Removed orphaned user_devices record for user_id: %', rec.user_id;
    END LOOP;
    
    FOR rec IN SELECT user_id FROM user_sessions WHERE user_id NOT IN (SELECT id FROM users)
    LOOP
        DELETE FROM user_sessions WHERE user_id = rec.user_id;
        RAISE NOTICE 'Removed orphaned user_sessions record for user_id: %', rec.user_id;
    END LOOP;
END$$;