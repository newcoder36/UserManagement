-- Add first_name and last_name to users table for direct access
-- This migration aligns with the updated User entity structure

-- Add name fields to users table
ALTER TABLE users 
ADD COLUMN first_name VARCHAR(100),
ADD COLUMN last_name VARCHAR(100);

-- Create indexes for name-based queries
CREATE INDEX idx_users_first_name ON users(first_name);
CREATE INDEX idx_users_last_name ON users(last_name);
CREATE INDEX idx_users_full_name ON users(first_name, last_name);

-- Update existing records from user_profiles (if any exist)
UPDATE users 
SET first_name = up.first_name,
    last_name = up.last_name
FROM user_profiles up
WHERE users.id = up.user_id
AND users.first_name IS NULL;

-- Add NOT NULL constraints after data migration
-- Note: In production, ensure all users have names before applying NOT NULL
-- ALTER TABLE users 
-- ALTER COLUMN first_name SET NOT NULL,
-- ALTER COLUMN last_name SET NOT NULL;

-- Add comments
COMMENT ON COLUMN users.first_name IS 'User first name for authentication and display';
COMMENT ON COLUMN users.last_name IS 'User last name for authentication and display';