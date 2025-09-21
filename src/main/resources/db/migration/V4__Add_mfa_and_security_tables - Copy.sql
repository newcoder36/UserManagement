-- Migration V4: Add MFA and Security Enhancement Tables
-- Story 2.1.1: Authentication Service Integration

-- MFA Configuration Table
CREATE TABLE mfa_configuration (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(50) NOT NULL,
    mfa_type VARCHAR(20) NOT NULL CHECK (mfa_type IN ('TOTP', 'SMS', 'EMAIL')),
    secret_key VARCHAR(500), -- Encrypted secret storage
    enabled BOOLEAN DEFAULT false,
    backup_codes TEXT[], -- Array of encrypted backup codes
    last_used TIMESTAMP,
    failed_attempts INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_user_mfa_type UNIQUE(user_id, mfa_type)
);

-- User Devices Table for Device Trust Management
CREATE TABLE user_devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(50) NOT NULL,
    device_fingerprint VARCHAR(255) NOT NULL,
    device_name VARCHAR(255),
    user_agent TEXT,
    ip_address INET,
    location VARCHAR(255),
    trusted BOOLEAN DEFAULT false,
    first_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_seen TIMESTAMP,
    trust_expiry TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_user_device UNIQUE(user_id, device_fingerprint)
);

-- Security Audit Logs Table
CREATE TABLE security_audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(50),
    session_id VARCHAR(255),
    event_type VARCHAR(50) NOT NULL,
    description TEXT,
    ip_address INET,
    user_agent TEXT,
    location VARCHAR(255),
    metadata JSONB,
    risk_level VARCHAR(20) CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Enhanced User Sessions Table
CREATE TABLE user_sessions (
    session_id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(50) NOT NULL,
    device_fingerprint VARCHAR(255),
    ip_address INET,
    user_agent TEXT,
    location VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_activity TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    active BOOLEAN DEFAULT true,
    attributes JSONB DEFAULT '{}'::jsonb
);

-- Session Settings Table
CREATE TABLE session_settings (
    user_id VARCHAR(50) PRIMARY KEY,
    max_concurrent_sessions INTEGER DEFAULT 3,
    session_timeout_minutes INTEGER DEFAULT 30,
    extend_on_activity BOOLEAN DEFAULT true,
    require_mfa_on_new_device BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Device Settings Table
CREATE TABLE device_settings (
    user_id VARCHAR(50) PRIMARY KEY,
    trust_duration_days INTEGER DEFAULT 30,
    require_mfa_for_untrusted BOOLEAN DEFAULT true,
    notify_new_devices BOOLEAN DEFAULT true,
    blocked_devices TEXT[] DEFAULT ARRAY[]::TEXT[],
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for optimal performance
CREATE INDEX idx_mfa_config_user_id ON mfa_configuration(user_id);
CREATE INDEX idx_mfa_config_enabled ON mfa_configuration(enabled) WHERE enabled = true;

CREATE INDEX idx_user_devices_user_id ON user_devices(user_id);
CREATE INDEX idx_user_devices_fingerprint ON user_devices(device_fingerprint);
CREATE INDEX idx_user_devices_trusted ON user_devices(trusted) WHERE trusted = true;
CREATE INDEX idx_user_devices_last_seen ON user_devices(last_seen);

CREATE INDEX idx_audit_logs_user_id ON security_audit_logs(user_id);
CREATE INDEX idx_audit_logs_timestamp ON security_audit_logs(timestamp);
CREATE INDEX idx_audit_logs_event_type ON security_audit_logs(event_type);
CREATE INDEX idx_audit_logs_risk_level ON security_audit_logs(risk_level);

CREATE INDEX idx_user_sessions_user_id ON user_sessions(user_id);
CREATE INDEX idx_user_sessions_active ON user_sessions(active) WHERE active = true;
CREATE INDEX idx_user_sessions_expires ON user_sessions(expires_at);
CREATE INDEX idx_user_sessions_device ON user_sessions(device_fingerprint);

-- Add foreign key constraints (assuming users table exists)
-- Note: Adjust table name if different in your schema
ALTER TABLE mfa_configuration ADD CONSTRAINT fk_mfa_config_user 
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE user_devices ADD CONSTRAINT fk_user_devices_user 
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE user_sessions ADD CONSTRAINT fk_user_sessions_user 
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE session_settings ADD CONSTRAINT fk_session_settings_user 
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE device_settings ADD CONSTRAINT fk_device_settings_user 
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- Create trigger for updating timestamps
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Apply update timestamp triggers
CREATE TRIGGER update_mfa_configuration_updated_at BEFORE UPDATE
    ON mfa_configuration FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_user_devices_updated_at BEFORE UPDATE
    ON user_devices FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_session_settings_updated_at BEFORE UPDATE
    ON session_settings FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_device_settings_updated_at BEFORE UPDATE
    ON device_settings FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Insert default settings for existing users
INSERT INTO session_settings (user_id)
SELECT id FROM users
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO device_settings (user_id)
SELECT id FROM users
ON CONFLICT (user_id) DO NOTHING;

-- Create materialized view for security dashboard
CREATE MATERIALIZED VIEW security_metrics_summary AS
SELECT 
    DATE_TRUNC('day', timestamp) as date,
    event_type,
    risk_level,
    COUNT(*) as event_count,
    COUNT(DISTINCT user_id) as unique_users
FROM security_audit_logs
WHERE timestamp >= CURRENT_DATE - INTERVAL '30 days'
GROUP BY DATE_TRUNC('day', timestamp), event_type, risk_level
ORDER BY date DESC;

-- Create index on materialized view
CREATE INDEX idx_security_metrics_summary_date ON security_metrics_summary(date);
CREATE INDEX idx_security_metrics_summary_event_type ON security_metrics_summary(event_type);

-- Refresh materialized view function
CREATE OR REPLACE FUNCTION refresh_security_metrics()
RETURNS void AS $$
BEGIN
    REFRESH MATERIALIZED VIEW CONCURRENTLY security_metrics_summary;
END;
$$ LANGUAGE plpgsql;

-- Schedule periodic refresh (requires pg_cron extension)
-- SELECT cron.schedule('refresh-security-metrics', '0 */6 * * *', 'SELECT refresh_security_metrics();');

-- Add comments for documentation
COMMENT ON TABLE mfa_configuration IS 'Multi-factor authentication configuration for users';
COMMENT ON TABLE user_devices IS 'Trusted device management for enhanced security';
COMMENT ON TABLE security_audit_logs IS 'Comprehensive security event logging';
COMMENT ON TABLE user_sessions IS 'Active user session management with device tracking';
COMMENT ON TABLE session_settings IS 'User-specific session management preferences';
COMMENT ON TABLE device_settings IS 'User-specific device trust preferences';

COMMENT ON COLUMN mfa_configuration.secret_key IS 'Encrypted TOTP secret or verification code';
COMMENT ON COLUMN mfa_configuration.backup_codes IS 'Array of encrypted backup codes for TOTP';
COMMENT ON COLUMN user_devices.device_fingerprint IS 'SHA-256 hash of device characteristics';
COMMENT ON COLUMN security_audit_logs.metadata IS 'Additional context data in JSON format';
COMMENT ON COLUMN user_sessions.attributes IS 'Session-specific metadata and preferences';