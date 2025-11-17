-- Drop Flyway schema history to force re-migration
-- Use this if Flyway thinks migrations have run but tables don't exist
-- Usage: mysql -u pipeline -p pipeline < drop-flyway-history.sql

USE pipeline;

-- Drop Flyway's schema history table
-- This will allow Flyway to re-run all migrations from scratch
DROP TABLE IF EXISTS flyway_schema_history;

