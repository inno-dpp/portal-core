-- Database initialization script for the portal
-- This script will be executed when PostgreSQL container starts for the first time

-- Create database if it doesn't exist (handled by POSTGRES_DB environment variable)
-- Create user if it doesn't exist (handled by POSTGRES_USER environment variable)

-- Grant all privileges on database to user
GRANT ALL PRIVILEGES ON DATABASE portal TO portal;

-- Grant usage and create privileges on schema
GRANT USAGE ON SCHEMA public TO portal;
GRANT CREATE ON SCHEMA public TO portal;

-- Set default privileges for tables created in the future
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON TABLES TO portal;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON SEQUENCES TO portal;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON FUNCTIONS TO portal;

-- Ensure the user can create and drop tables (needed for Spring Boot schema management)
GRANT ALL ON ALL TABLES IN SCHEMA public TO portal;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO portal;

-- Set timezone (optional - adjust as needed)
-- ALTER DATABASE portal SET timezone TO 'UTC';