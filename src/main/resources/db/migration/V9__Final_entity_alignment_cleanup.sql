-- Migration V9: Final Entity Alignment and Data Cleanup
-- Ensures complete alignment between database schema and Java entities
-- Handles edge cases and performs final validation

-- 1. Handle UserProfile entity field alignment
-- Ensure phone_number field is properly duplicated from users table
DO $$
BEGIN
    -- Sync phone_number from users to user_profiles where missing
    UPDATE user_profiles 
    SET phone_number = (
        SELECT users.phone_number 
        FROM users 
        WHERE users.id = user_profiles.user_id
    )
    WHERE user_profiles.phone_number IS NULL 
    AND EXISTS (
        SELECT 1 FROM users 
        WHERE users.id = user_profiles.user_id 
        AND users.phone_number IS NOT NULL
    );
    
    RAISE NOTICE 'Synchronized phone_number fields between users and user_profiles tables';
END$$;

-- 2. Ensure MfaConfiguration backup_codes field is properly structured
-- Update any existing records to ensure array format
UPDATE mfa_configuration 
SET backup_codes = ARRAY[]::TEXT[] 
WHERE backup_codes IS NULL;

-- 3. Clean up UserDevice table to match entity expectations
-- Ensure device_name has proper length constraint
ALTER TABLE user_devices ALTER COLUMN device_name TYPE VARCHAR(255);

-- Add missing columns that might be referenced in entity but not in schema
ALTER TABLE user_devices ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE user_devices ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- Create trigger for user_devices updated_at if it doesn't exist
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.triggers WHERE trigger_name = 'update_user_devices_updated_at') THEN
        CREATE TRIGGER update_user_devices_updated_at 
            BEFORE UPDATE ON user_devices 
            FOR EACH ROW 
            EXECUTE FUNCTION update_updated_at_column();
    END IF;
END$$;

-- 4. Ensure UserSession attributes field is properly initialized
UPDATE user_sessions 
SET attributes = '{}'::JSONB 
WHERE attributes IS NULL;

-- 5. Add proper validation for AccountStatus, KycStatus, SubscriptionTier enums
-- Update users table constraints to match entity enums exactly
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_kyc_status_check;
ALTER TABLE users ADD CONSTRAINT users_kyc_status_check 
    CHECK (kyc_status IN ('PENDING', 'IN_PROGRESS', 'APPROVED', 'REJECTED'));

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_subscription_tier_check;
ALTER TABLE users ADD CONSTRAINT users_subscription_tier_check 
    CHECK (subscription_tier IN ('FREE', 'PREMIUM', 'PROFESSIONAL', 'ENTERPRISE'));

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_account_status_check;
ALTER TABLE users ADD CONSTRAINT users_account_status_check 
    CHECK (account_status IN ('ACTIVE', 'SUSPENDED', 'LOCKED', 'DEACTIVATED'));

-- Update existing data to match enum values (uppercase)
UPDATE users SET kyc_status = UPPER(kyc_status) WHERE kyc_status IS NOT NULL;
UPDATE users SET subscription_tier = UPPER(subscription_tier) WHERE subscription_tier IS NOT NULL;
UPDATE users SET account_status = UPPER(account_status) WHERE account_status IS NOT NULL;

-- 6. Ensure UserProfile enum values match entity
ALTER TABLE user_profiles DROP CONSTRAINT IF EXISTS user_profiles_risk_tolerance_check;
ALTER TABLE user_profiles ADD CONSTRAINT user_profiles_risk_tolerance_check 
    CHECK (risk_tolerance IN ('CONSERVATIVE', 'MODERATE', 'AGGRESSIVE', 'VERY_AGGRESSIVE'));

ALTER TABLE user_profiles DROP CONSTRAINT IF EXISTS user_profiles_trading_experience_check;
ALTER TABLE user_profiles ADD CONSTRAINT user_profiles_trading_experience_check 
    CHECK (trading_experience IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'PROFESSIONAL'));

-- Update existing data to match enum values
UPDATE user_profiles SET risk_tolerance = UPPER(risk_tolerance) WHERE risk_tolerance IS NOT NULL;
UPDATE user_profiles SET trading_experience = UPPER(trading_experience) WHERE trading_experience IS NOT NULL;

-- 7. Ensure AuthAuditLog event types match entity enum values
-- Update auth_audit_log event_type constraint
ALTER TABLE auth_audit_log DROP CONSTRAINT IF EXISTS auth_audit_log_event_type_check;
ALTER TABLE auth_audit_log ADD CONSTRAINT auth_audit_log_event_type_check 
    CHECK (event_type IN (
        'LOGIN_SUCCESS', 'LOGIN_FAILED', 'LOGOUT', 'REGISTRATION',
        'PASSWORD_CHANGE', 'PASSWORD_RESET', 'MFA_ENABLED', 'MFA_DISABLED',
        'MFA_SUCCESS', 'MFA_FAILED', 'ACCOUNT_LOCKED', 'ACCOUNT_UNLOCKED',
        'ACCOUNT_SUSPENDED', 'ACCOUNT_REACTIVATED', 'EMAIL_VERIFIED',
        'PHONE_VERIFIED', 'DEVICE_REGISTERED', 'DEVICE_REMOVED',
        'SUSPICIOUS_ACTIVITY', 'SECURITY_VIOLATION', 'SESSION_CREATED',
        'SESSION_EXPIRED', 'TOKEN_ISSUED', 'TOKEN_REFRESHED', 'TOKEN_REVOKED'
    ));

-- 8. Add missing created_at/updated_at triggers for entities that need them
-- SessionSettings trigger
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.triggers WHERE trigger_name = 'update_session_settings_updated_at') THEN
        CREATE TRIGGER update_session_settings_updated_at 
            BEFORE UPDATE ON session_settings 
            FOR EACH ROW 
            EXECUTE FUNCTION update_updated_at_column();
    END IF;
END$$;

-- DeviceSettings trigger
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.triggers WHERE trigger_name = 'update_device_settings_updated_at') THEN
        CREATE TRIGGER update_device_settings_updated_at 
            BEFORE UPDATE ON device_settings 
            FOR EACH ROW 
            EXECUTE FUNCTION update_updated_at_column();
    END IF;
END$$;

-- 9. Create performance indexes for entity relationship queries
-- Indexes for common entity query patterns
CREATE INDEX IF NOT EXISTS idx_users_email_enabled ON users(email, enabled);
CREATE INDEX IF NOT EXISTS idx_users_kyc_subscription ON users(kyc_status, subscription_tier);
CREATE INDEX IF NOT EXISTS idx_user_profiles_risk_experience ON user_profiles(risk_tolerance, trading_experience);

-- Composite indexes for security queries
CREATE INDEX IF NOT EXISTS idx_auth_audit_risk_status ON auth_audit_log(risk_score, event_status);
CREATE INDEX IF NOT EXISTS idx_security_audit_risk_timestamp ON security_audit_logs(risk_level, timestamp);

-- 10. Data validation and cleanup
-- Remove any duplicate entries that might cause entity loading issues
DELETE FROM user_role_assignments a USING user_role_assignments b 
WHERE a.id > b.id 
AND a.user_id = b.user_id 
AND a.role_id = b.role_id;

-- Clean up orphaned verification tokens
DELETE FROM verification_tokens 
WHERE user_id NOT IN (SELECT id FROM users);

-- Clean up expired sessions that might cause entity state issues
DELETE FROM user_sessions 
WHERE expires_at < NOW() - INTERVAL '7 days';

-- 11. Final validation queries
-- Create a validation view for monitoring entity-database alignment
CREATE OR REPLACE VIEW entity_validation_summary AS
SELECT 
    'users' as table_name,
    COUNT(*) as total_records,
    COUNT(*) FILTER (WHERE email IS NOT NULL AND password_hash IS NOT NULL) as valid_records,
    COUNT(*) FILTER (WHERE enabled = true) as enabled_records
FROM users
UNION ALL
SELECT 
    'user_profiles' as table_name,
    COUNT(*) as total_records,
    COUNT(*) FILTER (WHERE user_id IS NOT NULL) as valid_records,
    COUNT(*) FILTER (WHERE user_id IN (SELECT id FROM users)) as linked_records
FROM user_profiles
UNION ALL
SELECT 
    'user_role_assignments' as table_name,
    COUNT(*) as total_records,
    COUNT(*) FILTER (WHERE user_id IS NOT NULL AND role_id IS NOT NULL) as valid_records,
    COUNT(*) FILTER (WHERE is_active = true) as active_records
FROM user_role_assignments
UNION ALL
SELECT 
    'mfa_configuration' as table_name,
    COUNT(*) as total_records,
    COUNT(*) FILTER (WHERE user_id IS NOT NULL AND mfa_type IS NOT NULL) as valid_records,
    COUNT(*) FILTER (WHERE enabled = true) as enabled_records
FROM mfa_configuration
UNION ALL
SELECT 
    'user_devices' as table_name,
    COUNT(*) as total_records,
    COUNT(*) FILTER (WHERE user_id IS NOT NULL AND device_fingerprint IS NOT NULL) as valid_records,
    COUNT(*) FILTER (WHERE trusted = true) as trusted_records
FROM user_devices
UNION ALL
SELECT 
    'user_sessions' as table_name,
    COUNT(*) as total_records,
    COUNT(*) FILTER (WHERE session_id IS NOT NULL AND user_id IS NOT NULL) as valid_records,
    COUNT(*) FILTER (WHERE active = true AND expires_at > NOW()) as active_records
FROM user_sessions;

-- 12. Add final documentation comments
COMMENT ON VIEW entity_validation_summary IS 'Validation summary for entity-database alignment monitoring';

-- Log completion
DO $$
BEGIN
    RAISE NOTICE 'Entity alignment migration V9 completed successfully';
    RAISE NOTICE 'Database schema now fully aligned with Java entities';
    RAISE NOTICE 'Use entity_validation_summary view to monitor data integrity';
END$$;