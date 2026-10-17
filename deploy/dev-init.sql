-- Runs once, the first time the dev database container starts with an empty volume.
-- It runs as the "postgres" superuser, after the "ereach" database has been created.
--
-- ereach_owner: owns the tables. Flyway connects as this role to run migrations.
-- ereach_app:   the role the application connects as. It does not own the tables,
--               so PostgreSQL row-level security always applies to it (ADR-003).
CREATE ROLE ereach_owner WITH LOGIN PASSWORD 'ereach_owner';
CREATE ROLE ereach_app WITH LOGIN PASSWORD 'ereach_app';
ALTER DATABASE ereach OWNER TO ereach_owner;
