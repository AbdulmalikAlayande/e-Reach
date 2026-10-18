-- V1: database extensions and default privileges.
-- Flyway runs this as ereach_owner (see spring.flyway.user in application.yml).

-- pg_trgm: fast "similar name" search for patients, drugs and ICD-10 codes.
-- btree_gist: lets PostgreSQL refuse two prices or tariffs whose date ranges overlap.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Every table and sequence that ereach_owner creates from now on can be read and
-- written by the application role. Later migrations take away what a table's
-- record category forbids (for example UPDATE on insert-only tables).
ALTER DEFAULT PRIVILEGES FOR ROLE ereach_owner IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE ON TABLES TO ereach_app;
ALTER DEFAULT PRIVILEGES FOR ROLE ereach_owner IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO ereach_app;
