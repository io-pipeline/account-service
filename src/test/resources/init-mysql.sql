-- Create additional database for Apicurio Registry
CREATE DATABASE IF NOT EXISTS apicurio_registry;

-- Create separate dev databases for each service (new architecture)
CREATE DATABASE IF NOT EXISTS pipeline_account_dev;
CREATE DATABASE IF NOT EXISTS pipeline_connector_dev;
CREATE DATABASE IF NOT EXISTS pipeline_connector_intake_dev;
CREATE DATABASE IF NOT EXISTS pipeline_repo_dev;

-- Create separate test databases for each service
CREATE DATABASE IF NOT EXISTS pipeline_account_test;
CREATE DATABASE IF NOT EXISTS pipeline_connector_test;
CREATE DATABASE IF NOT EXISTS pipeline_connector_intake_test;
CREATE DATABASE IF NOT EXISTS pipeline_repo_test;

-- Legacy databases (can be removed after migration completes)
CREATE DATABASE IF NOT EXISTS pipeline_dev;
CREATE DATABASE IF NOT EXISTS pipeline_test;

-- Grant privileges to the pipeline user for all databases
GRANT ALL PRIVILEGES ON apicurio_registry.* TO 'pipeline'@'%';
GRANT ALL PRIVILEGES ON pipeline.* TO 'pipeline'@'%';

-- New service databases
GRANT ALL PRIVILEGES ON pipeline_account_dev.* TO 'pipeline'@'%';
GRANT ALL PRIVILEGES ON pipeline_connector_dev.* TO 'pipeline'@'%';
GRANT ALL PRIVILEGES ON pipeline_connector_intake_dev.* TO 'pipeline'@'%';
GRANT ALL PRIVILEGES ON pipeline_repo_dev.* TO 'pipeline'@'%';
GRANT ALL PRIVILEGES ON pipeline_account_test.* TO 'pipeline'@'%';
GRANT ALL PRIVILEGES ON pipeline_connector_test.* TO 'pipeline'@'%';
GRANT ALL PRIVILEGES ON pipeline_connector_intake_test.* TO 'pipeline'@'%';
GRANT ALL PRIVILEGES ON pipeline_repo_test.* TO 'pipeline'@'%';

-- Legacy databases
GRANT ALL PRIVILEGES ON pipeline_dev.* TO 'pipeline'@'%';
GRANT ALL PRIVILEGES ON pipeline_test.* TO 'pipeline'@'%';

-- Flush privileges to ensure they take effect
FLUSH PRIVILEGES;