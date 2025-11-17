-- Repair migration: Create accounts table if it doesn't exist
-- This migration handles the case where V1 was marked as complete but the table wasn't created
CREATE TABLE IF NOT EXISTS accounts (
    account_id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Create indexes if they don't exist
-- MySQL doesn't support IF NOT EXISTS for indexes, so we check first
SET @dbname = DATABASE();
SET @tablename = 'accounts';
SET @indexname = 'idx_accounts_active';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.statistics 
     WHERE table_schema = @dbname 
     AND table_name = @tablename 
     AND index_name = @indexname) > 0,
    'SELECT 1',
    CONCAT('CREATE INDEX ', @indexname, ' ON ', @tablename, '(active)')
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @indexname = 'idx_accounts_name';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.statistics 
     WHERE table_schema = @dbname 
     AND table_name = @tablename 
     AND index_name = @indexname) > 0,
    'SELECT 1',
    CONCAT('CREATE INDEX ', @indexname, ' ON ', @tablename, '(name)')
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

