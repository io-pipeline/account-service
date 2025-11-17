-- Drop accounts table and related indexes
-- Run this script to clean up the accounts table so Flyway can recreate it properly
-- Usage: mysql -u pipeline -p pipeline < drop-accounts-table.sql

USE pipeline;

-- Drop indexes first (if they exist)
DROP INDEX IF EXISTS idx_accounts_name ON accounts;
DROP INDEX IF EXISTS idx_accounts_active ON accounts;

-- Drop the table
DROP TABLE IF EXISTS accounts;

