# facility-server

The facility server is the e-Reach application that runs inside one hospital. It holds all of that hospital's data in its own PostgreSQL database and serves the API the browser app uses. It works without the internet; the central platform only receives backups and audit seals when a connection exists.

**Status: skeleton.** The build, the database setup, migrations V1 and V1_1, and two tests are in place and passing. No feature modules exist yet; Sprint 1 adds facilities, users, audit and patient registration.

**Contact:** Abdulmalik Alayande.

## Run it

Run every command from the repository root, not from this folder, because the build starts at the root `pom.xml`.

```powershell
docker compose -f deploy/dev.yml up -d                    # development database on port 5433
.\mvnw.cmd -B verify                                     # build and run all tests
.\mvnw.cmd -pl backend/facility-server spring-boot:run   # start the server on port 8080
```

## Configuration

Settings live in `src/main/resources/application.yml`.

| Setting | Value in development | Why |
| --- | --- | --- |
| `spring.datasource.*` | `localhost:5433/ereach` as `ereach_app` | The application's own connection, as a user that owns no tables, so row-level security always applies |
| `spring.flyway.user`, `spring.flyway.password` | `ereach_owner` | Migrations run as the owner, the only user allowed to create tables |
| `spring.jpa.hibernate.ddl-auto` | `validate` | Hibernate checks the tables match the code and never changes them |
| `spring.jpa.open-in-view` | `false` | No database connection is held open while a response is written |
| `spring.session.jdbc.initialize-schema` | `never` | Spring Session must not create its own tables |
| `spring.modulith.events.jdbc.schema-initialization.enabled` | `false` | Spring Modulith must not create its own table; it is on by default and would fail as `ereach_app` |

The passwords here are for development only. A hospital's server gets its passwords from environment variables.

## Database migrations

Migrations live in `src/main/resources/db/migration` and run automatically, in order, when the server starts.

| File | What it does |
| --- | --- |
| `V1__extensions.sql` | Switches on `pg_trgm` and `btree_gist`; gives `ereach_app` read, insert and update on every table the owner creates from now on |
| `V1_1__framework_tables.sql` | Creates the tables Spring Modulith and Spring Session need, copied unchanged from each library's own schema file, and grants delete on those three tables only |

The rules for adding one:

- Name it `V<version>__<description>.sql`, with **two** underscores. With one, Flyway ignores the file without warning.
- Never edit a migration once it is merged. Add a new one instead.
- Every new table follows the record categories and the schema in the Batch 2 design document.
- When Spring Session or Spring Modulith is upgraded, compare its schema file with the copy in V1_1, and add a new migration for any change.

## Tests

| Test | What it proves |
| --- | --- |
| `FacilityServerApplicationTests.contextLoads` | The whole application starts against a real PostgreSQL 18, runs every migration as `ereach_owner`, and connects as `ereach_app` |
| `ModularityTests.modulesRespectTheirBoundaries` | No module uses another module's internal classes, and no two modules depend on each other in a loop |

Any test that needs the database extends `PostgresIntegrationTest`. It starts one PostgreSQL container for the whole test run and creates the two users from `src/test/resources/ereach-test-roles.sql`. Tests never connect as the database superuser, because PostgreSQL does not apply row-level security to a superuser.

## Where code goes

Each feature area is a package directly under `ng.ereach`, for example `ng.ereach.visit` or `ng.ereach.billing`. Spring Modulith treats each one as a module. Classes in the package's root are its public API; everything else goes in an `internal` sub-package that other modules may not use. The full module list and the rules every change must keep are in the root `ARCHITECTURE.md`.
