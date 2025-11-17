-- Create accounts table for multi-tenant support
CREATE TABLE accounts (
    account_id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Create index for active accounts
CREATE INDEX idx_accounts_active ON accounts(active);

-- Create index for account name lookup
CREATE INDEX idx_accounts_name ON accounts(name);
