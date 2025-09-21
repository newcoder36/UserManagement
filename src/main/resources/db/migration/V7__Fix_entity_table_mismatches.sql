-- Migration V7: Fix Entity-Table Structure Mismatches
-- Aligns database schema with current Java entities

-- 1. Fix MFA Configuration table to match MfaConfiguration entity
-- Drop existing mfa_configurations table from V1 (conflicting structure)
DROP TABLE IF EXISTS mfa_configurations CASCADE;

-- Recreate mfa_configuration table to match entity structure
-- Table already exists from V4, but we need to fix the user_id reference
ALTER TABLE mfa_configuration DROP CONSTRAINT IF EXISTS fk_mfa_config_user;
ALTER TABLE mfa_configuration ALTER COLUMN user_id TYPE INTEGER USING user_id::INTEGER;
ALTER TABLE mfa_configuration ADD CONSTRAINT fk_mfa_config_user 
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- 2. Fix User Devices table to match UserDevice entity
ALTER TABLE user_devices DROP CONSTRAINT IF EXISTS fk_user_devices_user;
ALTER TABLE user_devices ALTER COLUMN user_id TYPE INTEGER USING user_id::INTEGER;
ALTER TABLE user_devices ADD CONSTRAINT fk_user_devices_user 
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- 3. Fix User Sessions table to match UserSession entity  
ALTER TABLE user_sessions DROP CONSTRAINT IF EXISTS fk_user_sessions_user;
ALTER TABLE user_sessions ALTER COLUMN user_id TYPE INTEGER USING user_id::INTEGER;
ALTER TABLE user_sessions ADD CONSTRAINT fk_user_sessions_user 
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- 4. Fix Session Settings table to match SessionSettings entity
ALTER TABLE session_settings DROP CONSTRAINT IF EXISTS fk_session_settings_user;
ALTER TABLE session_settings ALTER COLUMN user_id TYPE INTEGER USING user_id::INTEGER;
ALTER TABLE session_settings ADD CONSTRAINT fk_session_settings_user 
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- 5. Fix Device Settings table to match DeviceSettings entity
ALTER TABLE device_settings DROP CONSTRAINT IF EXISTS fk_device_settings_user;
ALTER TABLE device_settings ALTER COLUMN user_id TYPE INTEGER USING user_id::INTEGER;
ALTER TABLE device_settings ADD CONSTRAINT fk_device_settings_user 
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- 6. Fix Security Audit Logs table to match SecurityAuditLog entity
ALTER TABLE security_audit_logs ALTER COLUMN user_id TYPE INTEGER USING user_id::INTEGER;
ALTER TABLE security_audit_logs ADD CONSTRAINT fk_security_audit_logs_user 
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL;

-- Add missing columns to security_audit_logs to match entity
ALTER TABLE security_audit_logs 
ADD COLUMN IF NOT EXISTS severity VARCHAR(20),
ADD COLUMN IF NOT EXISTS event_details JSONB,
ADD COLUMN IF NOT EXISTS risk_score INTEGER;

-- Update existing data
UPDATE security_audit_logs SET severity = 'MEDIUM' WHERE severity IS NULL;
UPDATE security_audit_logs SET event_details = '{}' WHERE event_details IS NULL;
UPDATE security_audit_logs SET risk_score = 50 WHERE risk_score IS NULL;

-- 7. Remove duplicate user_devices table from V1 if it exists
DROP TABLE IF EXISTS user_devices_v1 CASCADE;

-- 8. Ensure user_profiles has phone_number field matching entity
ALTER TABLE user_profiles 
ADD COLUMN IF NOT EXISTS phone_number VARCHAR(20);

-- Update user_profiles phone_number from users table if needed
UPDATE user_profiles 
SET phone_number = u.phone_number
FROM users u
WHERE user_profiles.user_id = u.id
AND user_profiles.phone_number IS NULL
AND u.phone_number IS NOT NULL;

-- 9. Clean up any existing inconsistent data
-- Update session default settings for existing users
INSERT INTO session_settings (user_id)
SELECT id FROM users
WHERE id NOT IN (SELECT user_id FROM session_settings)
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO device_settings (user_id)
SELECT id FROM users
WHERE id NOT IN (SELECT user_id FROM device_settings)
ON CONFLICT (user_id) DO NOTHING;

-- 10. Add missing indexes for foreign key relationships
CREATE INDEX IF NOT EXISTS idx_mfa_configuration_user_id_fk ON mfa_configuration(user_id);
CREATE INDEX IF NOT EXISTS idx_user_devices_user_id_fk ON user_devices(user_id);
CREATE INDEX IF NOT EXISTS idx_user_sessions_user_id_fk ON user_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_session_settings_user_id_fk ON session_settings(user_id);
CREATE INDEX IF NOT EXISTS idx_device_settings_user_id_fk ON device_settings(user_id);
CREATE INDEX IF NOT EXISTS idx_security_audit_logs_user_id_fk ON security_audit_logs(user_id);

-- Add comments for documentation
COMMENT ON TABLE mfa_configuration IS 'Multi-factor authentication configurations aligned with MfaConfiguration entity';
COMMENT ON TABLE user_devices IS 'User device management aligned with UserDevice entity';
COMMENT ON TABLE user_sessions IS 'Active user sessions aligned with UserSession entity';
COMMENT ON TABLE session_settings IS 'User session preferences aligned with SessionSettings entity';
COMMENT ON TABLE device_settings IS 'User device preferences aligned with DeviceSettings entity';
COMMENT ON TABLE security_audit_logs IS 'Security audit events aligned with SecurityAuditLog entity';

COMMENT ON COLUMN mfa_configuration.user_id IS 'Foreign key reference to users.id matching entity relationship';
COMMENT ON COLUMN user_devices.user_id IS 'Foreign key reference to users.id matching entity relationship';
COMMENT ON COLUMN user_sessions.user_id IS 'Foreign key reference to users.id matching entity relationship';
COMMENT ON COLUMN session_settings.user_id IS 'Foreign key reference to users.id matching entity relationship';
COMMENT ON COLUMN device_settings.user_id IS 'Foreign key reference to users.id matching entity relationship';
COMMENT ON COLUMN security_audit_logs.user_id IS 'Foreign key reference to users.id matching entity relationship';