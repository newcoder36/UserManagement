-- TradeMaster Authentication Service - Initial Schema
-- Version: 1.0
-- Description: Create users table with security and audit fields

-- Users table - Primary authentication entity
CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    kyc_status VARCHAR(50) DEFAULT 'pending' CHECK (kyc_status IN ('pending', 'in_progress', 'approved', 'rejected')),
    subscription_tier VARCHAR(50) DEFAULT 'free' CHECK (subscription_tier IN ('free', 'premium', 'professional', 'enterprise')),
    account_status VARCHAR(50) DEFAULT 'active' CHECK (account_status IN ('active', 'suspended', 'locked', 'deactivated')),
    email_verified BOOLEAN DEFAULT FALSE,
    phone_number VARCHAR(20),
    phone_verified BOOLEAN DEFAULT FALSE,
    failed_login_attempts INTEGER DEFAULT 0,
    account_locked_until TIMESTAMP,
    password_changed_at TIMESTAMP DEFAULT NOW(),
    last_login_at TIMESTAMP,
    last_login_ip INET,
    device_fingerprint VARCHAR(512),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    created_by VARCHAR(100) DEFAULT 'system',
    updated_by VARCHAR(100) DEFAULT 'system'
);

-- Create indexes for performance
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_kyc_status ON users(kyc_status);
CREATE INDEX idx_users_subscription_tier ON users(subscription_tier);
CREATE INDEX idx_users_account_status ON users(account_status);
CREATE INDEX idx_users_created_at ON users(created_at);
CREATE INDEX idx_users_last_login_at ON users(last_login_at);

-- User profiles table - Extended user information
CREATE TABLE user_profiles (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    date_of_birth DATE,
    country_code VARCHAR(3),
    timezone VARCHAR(50) DEFAULT 'UTC',
    risk_tolerance VARCHAR(20) CHECK (risk_tolerance IN ('conservative', 'moderate', 'aggressive', 'very_aggressive')),
    trading_experience VARCHAR(20) CHECK (trading_experience IN ('beginner', 'intermediate', 'advanced', 'professional')),
    annual_income_range VARCHAR(50),
    net_worth_range VARCHAR(50),
    investment_goals TEXT[],
    behavioral_settings JSONB DEFAULT '{}',
    preferences JSONB DEFAULT '{}',
    kyc_documents JSONB DEFAULT '{}',
    compliance_flags JSONB DEFAULT '{}',
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    created_by VARCHAR(100) DEFAULT 'system',
    updated_by VARCHAR(100) DEFAULT 'system',
    UNIQUE(user_id)
);

-- Create indexes for user profiles
CREATE INDEX idx_user_profiles_user_id ON user_profiles(user_id);
CREATE INDEX idx_user_profiles_risk_tolerance ON user_profiles(risk_tolerance);
CREATE INDEX idx_user_profiles_trading_experience ON user_profiles(trading_experience);
CREATE INDEX idx_user_profiles_country_code ON user_profiles(country_code);

-- User roles table for RBAC
CREATE TABLE user_roles (
    id SERIAL PRIMARY KEY,
    role_name VARCHAR(50) UNIQUE NOT NULL,
    description TEXT,
    permissions JSONB DEFAULT '{}',
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- User role assignments
CREATE TABLE user_role_assignments (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id INTEGER NOT NULL REFERENCES user_roles(id) ON DELETE CASCADE,
    assigned_at TIMESTAMP DEFAULT NOW(),
    assigned_by VARCHAR(100) DEFAULT 'system',
    expires_at TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    UNIQUE(user_id, role_id)
);

-- Create indexes for RBAC
CREATE INDEX idx_user_role_assignments_user_id ON user_role_assignments(user_id);
CREATE INDEX idx_user_role_assignments_role_id ON user_role_assignments(role_id);
CREATE INDEX idx_user_role_assignments_active ON user_role_assignments(is_active);

-- Insert default roles
INSERT INTO user_roles (role_name, description, permissions) VALUES
('USER', 'Standard user with basic trading permissions', '{"trading": ["view_portfolio", "place_orders", "view_market_data"], "profile": ["update_profile", "view_profile"]}'),
('PREMIUM_USER', 'Premium user with advanced features', '{"trading": ["view_portfolio", "place_orders", "view_market_data", "advanced_analytics", "priority_support"], "profile": ["update_profile", "view_profile", "export_data"]}'),
('PROFESSIONAL_USER', 'Professional user with institutional features', '{"trading": ["view_portfolio", "place_orders", "view_market_data", "advanced_analytics", "priority_support", "api_access", "bulk_operations"], "profile": ["update_profile", "view_profile", "export_data", "compliance_reports"]}'),
('ADMIN', 'System administrator with full access', '{"system": ["user_management", "system_config", "audit_logs"], "trading": ["*"], "profile": ["*"]}');

-- Password history table for compliance
CREATE TABLE password_history (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Create index for password history
CREATE INDEX idx_password_history_user_id ON password_history(user_id);
CREATE INDEX idx_password_history_created_at ON password_history(created_at);

-- MFA configurations table
CREATE TABLE mfa_configurations (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    mfa_type VARCHAR(20) NOT NULL CHECK (mfa_type IN ('SMS', 'EMAIL', 'TOTP', 'BIOMETRIC')),
    secret_key VARCHAR(512),
    backup_codes TEXT[],
    is_enabled BOOLEAN DEFAULT FALSE,
    verified_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(user_id, mfa_type)
);

-- Create indexes for MFA
CREATE INDEX idx_mfa_configurations_user_id ON mfa_configurations(user_id);
CREATE INDEX idx_mfa_configurations_type ON mfa_configurations(mfa_type);
CREATE INDEX idx_mfa_configurations_enabled ON mfa_configurations(is_enabled);

-- Device management table
CREATE TABLE user_devices (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_fingerprint VARCHAR(512) NOT NULL,
    device_name VARCHAR(200),
    device_type VARCHAR(50) CHECK (device_type IN ('mobile', 'desktop', 'tablet', 'api')),
    user_agent TEXT,
    ip_address INET,
    location JSONB,
    is_trusted BOOLEAN DEFAULT FALSE,
    first_seen_at TIMESTAMP DEFAULT NOW(),
    last_seen_at TIMESTAMP DEFAULT NOW(),
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(user_id, device_fingerprint)
);

-- Create indexes for device management
CREATE INDEX idx_user_devices_user_id ON user_devices(user_id);
CREATE INDEX idx_user_devices_fingerprint ON user_devices(device_fingerprint);
CREATE INDEX idx_user_devices_trusted ON user_devices(is_trusted);
CREATE INDEX idx_user_devices_last_seen ON user_devices(last_seen_at);

-- Updated at trigger function
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create triggers for updated_at columns
CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_user_profiles_updated_at BEFORE UPDATE ON user_profiles FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_user_roles_updated_at BEFORE UPDATE ON user_roles FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_mfa_configurations_updated_at BEFORE UPDATE ON mfa_configurations FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Add comments for documentation
COMMENT ON TABLE users IS 'Primary user authentication and account management table';
COMMENT ON TABLE user_profiles IS 'Extended user profile information for KYC and personalization';
COMMENT ON TABLE user_roles IS 'Role definitions for role-based access control (RBAC)';
COMMENT ON TABLE user_role_assignments IS 'User-to-role assignments for RBAC implementation';
COMMENT ON TABLE password_history IS 'Password history for compliance and security policies';
COMMENT ON TABLE mfa_configurations IS 'Multi-factor authentication configurations per user';
COMMENT ON TABLE user_devices IS 'Device fingerprinting and trusted device management';

COMMENT ON COLUMN users.password_hash IS 'BCrypt hashed password with salt';
COMMENT ON COLUMN users.kyc_status IS 'Know Your Customer verification status';
COMMENT ON COLUMN users.subscription_tier IS 'User subscription level for feature access';
COMMENT ON COLUMN users.device_fingerprint IS 'Device fingerprint for security tracking';
COMMENT ON COLUMN user_profiles.behavioral_settings IS 'AI behavioral analysis preferences and settings';
COMMENT ON COLUMN user_profiles.preferences IS 'User interface and trading preferences';
COMMENT ON COLUMN user_profiles.kyc_documents IS 'Encrypted KYC document metadata and status';
COMMENT ON COLUMN user_profiles.compliance_flags IS 'Regulatory compliance flags and status';
COMMENT ON COLUMN mfa_configurations.secret_key IS 'Encrypted MFA secret key (TOTP/HOTP)';
COMMENT ON COLUMN mfa_configurations.backup_codes IS 'Encrypted backup codes for account recovery';