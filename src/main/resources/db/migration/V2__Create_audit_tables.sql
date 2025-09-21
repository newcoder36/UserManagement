-- TradeMaster Authentication Service - Audit and Logging Schema
-- Version: 2.0
-- Description: Create audit tables for compliance and security monitoring

-- Authentication audit log
CREATE TABLE auth_audit_log1 (
    id SERIAL PRIMARY KEY,
    user_id INTEGER REFERENCES users(id),
    event_type VARCHAR(50) NOT NULL CHECK (event_type IN (
        'LOGIN_SUCCESS', 'LOGIN_FAILED', 'LOGOUT', 'REGISTRATION',
        'PASSWORD_CHANGE', 'PASSWORD_RESET', 'MFA_ENABLED', 'MFA_DISABLED',
        'MFA_SUCCESS', 'MFA_FAILED', 'ACCOUNT_LOCKED', 'ACCOUNT_UNLOCKED',
        'ACCOUNT_SUSPENDED', 'ACCOUNT_REACTIVATED', 'EMAIL_VERIFIED',
        'PHONE_VERIFIED', 'DEVICE_REGISTERED', 'DEVICE_REMOVED',
        'SUSPICIOUS_ACTIVITY', 'SECURITY_VIOLATION', 'SESSION_CREATED',
        'SESSION_EXPIRED', 'TOKEN_ISSUED', 'TOKEN_REFRESHED', 'TOKEN_REVOKED'
    )),
    event_status VARCHAR(20) DEFAULT 'SUCCESS' CHECK (event_status IN ('SUCCESS', 'FAILED', 'PENDING', 'BLOCKED')),
    ip_address INET,
    user_agent TEXT,
    device_fingerprint VARCHAR(512),
    location JSONB,
    details JSONB DEFAULT '{}',
    risk_score INTEGER DEFAULT 0 CHECK (risk_score >= 0 AND risk_score <= 100),
    session_id VARCHAR(128),
    correlation_id UUID DEFAULT gen_random_uuid(),
    created_at TIMESTAMP DEFAULT NOW(),
    processed_at TIMESTAMP,
    
    -- Blockchain signature for immutability (compliance requirement)
    blockchain_hash VARCHAR(64),
    previous_hash VARCHAR(64),
    signature VARCHAR(512)
);

-- Create indexes for audit log performance
CREATE INDEX idx_auth_audit_log_user_id ON auth_audit_log(user_id);
CREATE INDEX idx_auth_audit_log_event_type ON auth_audit_log(event_type);
CREATE INDEX idx_auth_audit_log_event_status ON auth_audit_log(event_status);
CREATE INDEX idx_auth_audit_log_created_at ON auth_audit_log(created_at);
CREATE INDEX idx_auth_audit_log_ip_address ON auth_audit_log(ip_address);
CREATE INDEX idx_auth_audit_log_risk_score ON auth_audit_log(risk_score);
CREATE INDEX idx_auth_audit_log_correlation_id ON auth_audit_log(correlation_id);
CREATE INDEX idx_auth_audit_log_session_id ON auth_audit_log(session_id);

-- Security events table for real-time monitoring
CREATE TABLE security_events (
    id SERIAL PRIMARY KEY,
    user_id INTEGER REFERENCES users(id),
    event_category VARCHAR(50) NOT NULL CHECK (event_category IN (
        'AUTHENTICATION', 'AUTHORIZATION', 'DATA_ACCESS', 'CONFIGURATION',
        'NETWORK', 'MALWARE', 'INTRUSION', 'COMPLIANCE', 'PRIVACY'
    )),
    severity_level VARCHAR(20) NOT NULL CHECK (severity_level IN (
        'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'
    )),
    threat_type VARCHAR(50),
    source_ip INET,
    target_resource VARCHAR(200),
    attack_vector VARCHAR(100),
    detection_method VARCHAR(100),
    mitigation_action VARCHAR(200),
    is_resolved BOOLEAN DEFAULT FALSE,
    false_positive BOOLEAN DEFAULT FALSE,
    analyst_notes TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    resolved_at TIMESTAMP,
    resolved_by VARCHAR(100)
);

-- Create indexes for security events
CREATE INDEX idx_security_events_user_id ON security_events(user_id);
CREATE INDEX idx_security_events_category ON security_events(event_category);
CREATE INDEX idx_security_events_severity ON security_events(severity_level);
CREATE INDEX idx_security_events_resolved ON security_events(is_resolved);
CREATE INDEX idx_security_events_created_at ON security_events(created_at);
CREATE INDEX idx_security_events_source_ip ON security_events(source_ip);

-- Rate limiting violations table
CREATE TABLE rate_limit_violations (
    id SERIAL PRIMARY KEY,
    user_id INTEGER REFERENCES users(id),
    ip_address INET NOT NULL,
    endpoint VARCHAR(200) NOT NULL,
    request_count INTEGER NOT NULL,
    limit_threshold INTEGER NOT NULL,
    time_window_minutes INTEGER NOT NULL,
    violation_type VARCHAR(50) CHECK (violation_type IN ('USER_LIMIT', 'IP_LIMIT', 'ENDPOINT_LIMIT', 'GLOBAL_LIMIT')),
    user_agent TEXT,
    blocked_duration_minutes INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW(),
    unblocked_at TIMESTAMP
);

-- Create indexes for rate limiting
CREATE INDEX idx_rate_limit_violations_user_id ON rate_limit_violations(user_id);
CREATE INDEX idx_rate_limit_violations_ip ON rate_limit_violations(ip_address);
CREATE INDEX idx_rate_limit_violations_endpoint ON rate_limit_violations(endpoint);
CREATE INDEX idx_rate_limit_violations_type ON rate_limit_violations(violation_type);

-- Session tracking table
CREATE TABLE user_sessions (
    id SERIAL PRIMARY KEY,
    session_id VARCHAR(128) UNIQUE NOT NULL,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_fingerprint VARCHAR(512),
    ip_address INET,
    user_agent TEXT,
    location JSONB,
    created_at TIMESTAMP DEFAULT NOW(),
    last_activity_at TIMESTAMP DEFAULT NOW(),
    expires_at TIMESTAMP NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    logout_reason VARCHAR(50) CHECK (logout_reason IN ('USER_LOGOUT', 'TIMEOUT', 'FORCE_LOGOUT', 'SECURITY_VIOLATION', 'CONCURRENT_LIMIT')),
    logout_at TIMESTAMP
);

-- Create indexes for session tracking
CREATE INDEX idx_user_sessions_session_id ON user_sessions(session_id);
CREATE INDEX idx_user_sessions_user_id ON user_sessions(user_id);
CREATE INDEX idx_user_sessions_active ON user_sessions(is_active);
CREATE INDEX idx_user_sessions_expires_at ON user_sessions(expires_at);
CREATE INDEX idx_user_sessions_last_activity ON user_sessions(last_activity_at);
CREATE INDEX idx_user_sessions_device ON user_sessions(device_fingerprint);

-- Compliance reporting table for SEBI requirements
CREATE TABLE compliance_reports (
    id SERIAL PRIMARY KEY,
    report_type VARCHAR(50) NOT NULL CHECK (report_type IN (
        'DAILY_ACCESS', 'WEEKLY_ACTIVITY', 'MONTHLY_SUMMARY', 
        'SECURITY_INCIDENTS', 'USER_ONBOARDING', 'ACCOUNT_CHANGES',
        'SUSPICIOUS_ACTIVITIES', 'DATA_ACCESS_LOG'
    )),
    report_period_start TIMESTAMP NOT NULL,
    report_period_end TIMESTAMP NOT NULL,
    total_records INTEGER DEFAULT 0,
    report_data JSONB NOT NULL,
    report_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    submitted_to_authority BOOLEAN DEFAULT FALSE,
    submission_date TIMESTAMP,
    authority_acknowledgment VARCHAR(200)
);

-- Create indexes for compliance reports
CREATE INDEX idx_compliance_reports_type ON compliance_reports(report_type);
CREATE INDEX idx_compliance_reports_period_start ON compliance_reports(report_period_start);
CREATE INDEX idx_compliance_reports_period_end ON compliance_reports(report_period_end);
CREATE INDEX idx_compliance_reports_created_at ON compliance_reports(created_at);
CREATE INDEX idx_compliance_reports_submitted ON compliance_reports(submitted_to_authority);

-- API access log for detailed tracking
CREATE TABLE api_access_log (
    id SERIAL PRIMARY KEY,
    user_id INTEGER REFERENCES users(id),
    request_id UUID DEFAULT gen_random_uuid(),
    method VARCHAR(10) NOT NULL,
    endpoint VARCHAR(500) NOT NULL,
    request_headers JSONB,
    request_body_hash VARCHAR(64),
    response_status INTEGER,
    response_time_ms INTEGER,
    bytes_sent INTEGER DEFAULT 0,
    bytes_received INTEGER DEFAULT 0,
    ip_address INET,
    user_agent TEXT,
    api_key_id VARCHAR(100),
    rate_limit_remaining INTEGER,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Create indexes for API access log
CREATE INDEX idx_api_access_log_user_id ON api_access_log(user_id);
CREATE INDEX idx_api_access_log_endpoint ON api_access_log(endpoint);
CREATE INDEX idx_api_access_log_status ON api_access_log(response_status);
CREATE INDEX idx_api_access_log_created_at ON api_access_log(created_at);
CREATE INDEX idx_api_access_log_request_id ON api_access_log(request_id);
CREATE INDEX idx_api_access_log_ip_address ON api_access_log(ip_address);

-- Data retention policy table
CREATE TABLE data_retention_policies (
    id SERIAL PRIMARY KEY,
    table_name VARCHAR(100) NOT NULL,
    retention_period_days INTEGER NOT NULL,
    archive_after_days INTEGER,
    delete_after_days INTEGER,
    last_cleanup_at TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Insert default retention policies (7 years for financial compliance)
INSERT INTO data_retention_policies (table_name, retention_period_days, archive_after_days, delete_after_days) VALUES
('auth_audit_log', 2555, 365, 2555), -- 7 years total, archive after 1 year
('security_events', 2555, 365, 2555),
('rate_limit_violations', 365, 90, 365), -- 1 year total, archive after 3 months
('user_sessions', 90, 30, 90), -- 3 months total, archive after 1 month
('compliance_reports', 2555, 365, 2555),
('api_access_log', 365, 90, 365);

-- Function to generate blockchain hash for audit trail integrity
CREATE OR REPLACE FUNCTION generate_audit_hash(record_data TEXT, previous_hash TEXT DEFAULT '')
RETURNS TEXT AS $$
BEGIN
    RETURN encode(sha256((record_data || previous_hash || NOW()::TEXT)::bytea), 'hex');
END;
$$ LANGUAGE plpgsql;

-- Function to update last activity timestamp
CREATE OR REPLACE FUNCTION update_last_activity()
RETURNS TRIGGER AS $$
BEGIN
    NEW.last_activity_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger for session activity updates
CREATE TRIGGER update_session_activity 
    BEFORE UPDATE ON user_sessions 
    FOR EACH ROW 
    EXECUTE FUNCTION update_last_activity();

-- Add updated_at trigger for compliance reports
CREATE TRIGGER update_compliance_reports_updated_at 
    BEFORE UPDATE ON compliance_reports 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();

-- Function for automatic audit log entry
CREATE OR REPLACE FUNCTION create_audit_entry(
    p_user_id INTEGER,
    p_event_type VARCHAR(50),
    p_event_status VARCHAR(20),
    p_ip_address INET,
    p_user_agent TEXT,
    p_device_fingerprint VARCHAR(512),
    p_details JSONB DEFAULT '{}',
    p_session_id VARCHAR(128) DEFAULT NULL
) RETURNS INTEGER AS $$
DECLARE
    audit_id INTEGER;
    prev_hash VARCHAR(64);
BEGIN
    -- Get the previous hash for blockchain continuity
    SELECT blockchain_hash INTO prev_hash 
    FROM auth_audit_log 
    ORDER BY id DESC 
    LIMIT 1;
    
    -- Insert new audit record
    INSERT INTO auth_audit_log (
        user_id, event_type, event_status, ip_address, user_agent,
        device_fingerprint, details, session_id, previous_hash,
        blockchain_hash
    ) VALUES (
        p_user_id, p_event_type, p_event_status, p_ip_address, p_user_agent,
        p_device_fingerprint, p_details, p_session_id, prev_hash,
        generate_audit_hash(p_event_type || p_user_id::TEXT || p_event_status, COALESCE(prev_hash, ''))
    ) RETURNING id INTO audit_id;
    
    RETURN audit_id;
END;
$$ LANGUAGE plpgsql;

-- Add comments for documentation
COMMENT ON TABLE auth_audit_log IS 'Comprehensive audit trail for all authentication events with blockchain integrity';
COMMENT ON TABLE security_events IS 'Security incident tracking and threat detection events';
COMMENT ON TABLE rate_limit_violations IS 'Rate limiting violations for abuse detection and prevention';
COMMENT ON TABLE user_sessions IS 'Active session tracking with detailed metadata';
COMMENT ON TABLE compliance_reports IS 'Regulatory compliance reports for SEBI and other authorities';
COMMENT ON TABLE api_access_log IS 'Detailed API access logging for security and performance monitoring';
COMMENT ON TABLE data_retention_policies IS 'Data retention policies for automated cleanup and archival';

COMMENT ON COLUMN auth_audit_log.blockchain_hash IS 'SHA-256 hash for audit trail integrity verification';
COMMENT ON COLUMN auth_audit_log.risk_score IS 'Risk score (0-100) based on behavioral analysis';
COMMENT ON COLUMN security_events.detection_method IS 'Method used to detect the security event';
COMMENT ON COLUMN user_sessions.logout_reason IS 'Reason for session termination for security analysis';