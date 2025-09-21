-- Create verification tokens table for email verification and password reset
CREATE TABLE verification_tokens (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(255) NOT NULL UNIQUE,
    token_type VARCHAR(50) NOT NULL CHECK (token_type IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET', 'MFA_BACKUP')),
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    ip_address INET,
    user_agent TEXT
);

-- Indexes for performance
CREATE INDEX idx_verification_tokens_user_id ON verification_tokens(user_id);
CREATE INDEX idx_verification_tokens_token ON verification_tokens(token);
CREATE INDEX idx_verification_tokens_type_expires ON verification_tokens(token_type, expires_at);

-- Comments for documentation
COMMENT ON TABLE verification_tokens IS 'Stores email verification and password reset tokens';
COMMENT ON COLUMN verification_tokens.token IS 'Secure random token for verification';
COMMENT ON COLUMN verification_tokens.token_type IS 'Type of verification token';
COMMENT ON COLUMN verification_tokens.expires_at IS 'Token expiration timestamp';
COMMENT ON COLUMN verification_tokens.used_at IS 'When the token was used (null if unused)';
COMMENT ON COLUMN verification_tokens.ip_address IS 'IP address when token was created';
COMMENT ON COLUMN verification_tokens.user_agent IS 'User agent when token was created';