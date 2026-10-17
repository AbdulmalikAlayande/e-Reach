-- Runs once in the test database container, before any test starts.
-- It runs as the container's superuser, inside the "ereach" database.
--
-- ereach_owner: owns the tables. Flyway connects as this role to run migrations.
-- ereach_app:   the role the application connects as. It does not own the tables,
--               so PostgreSQL row-level security always applies to it (ADR-003).
CREATE ROLE ereach_owner WITH LOGIN PASSWORD 'ereach_owner';
CREATE ROLE ereach_app WITH LOGIN PASSWORD 'ereach_app';
ALTER DATABASE ereach OWNER TO ereach_owner;
