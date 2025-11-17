#!/bin/bash
# Reset accounts table and Flyway history
# This script drops both the accounts table and Flyway history to force a clean migration

set -e

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-pipeline}"
DB_USER="${DB_USER:-pipeline}"
DB_PASS="${DB_PASSWORD:-password}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "Resetting accounts schema in database: $DB_NAME"
echo "This will drop the accounts table and Flyway history..."

# Drop accounts table
echo "Dropping accounts table..."
mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASS" "$DB_NAME" <<EOF
USE $DB_NAME;
DROP INDEX IF EXISTS idx_accounts_name ON accounts;
DROP INDEX IF EXISTS idx_accounts_active ON accounts;
DROP TABLE IF EXISTS accounts;
EOF

# Drop Flyway history
echo "Dropping Flyway schema history..."
mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASS" "$DB_NAME" <<EOF
USE $DB_NAME;
DROP TABLE IF EXISTS flyway_schema_history;
EOF

echo "Done! The accounts table and Flyway history have been dropped."
echo "Restart the service and Flyway will recreate everything from scratch."

