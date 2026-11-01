# Facility Server

The facility server is the e-Reach application that runs inside one hospital. It holds all of that hospital's data in its own PostgreSQL database and serves the API the browser app uses. It works without the internet; the central platform only receives backups and audit seals when a connection exists.

**Status: ER-006 done, pending a full green run; ER-004b and ER-007 in progress.** Migrations V1, V1_1, V2, V3 and the states-and-LGAs reference data are in place, with row-level security on every facility table, the facility context and transaction hook, and the first entities. The RFC 9457 error module lives in `shared`. The `iam` module has its tables and the authenticator-app (TOTP) setup; sign-in comes next.

**Contact:** Abdulmalik Alayande.

## Run it

Run every Maven command from `server/`, not from this folder, because the build starts at `server/pom.xml`.

```powershell
docker compose -f ../deploy/dev.yml up -d          # development database on port 5433
.\mvnw.cmd -B verify                              # build and run all tests
.\mvnw.cmd -pl facility-server spring-boot:run    # start the server on port 8080
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
| `V2__facility_and_clinic.sql` | The `set_audit_columns` trigger function; `state` and `lga` (read-only for the application); `facility`, `facility_setting` and `clinic` with their checks, partial unique indexes and row-level security |
| `V3__identity_and_access_management.sql` | `user_account` with the four system actors, `mfa_method`, `mfa_challenge`, `sign_in_throttle`, `user_security_key`, `practitioner_profile`, `facility_membership` and `role_assignment`, with the membership policies |
| `R__states_and_LGAs.sql` | The 37 states and 774 LGAs, reloaded by Flyway whenever the file changes |

The rules for adding one:

- Name it `V<version>__<description>.sql`, with **two** underscores. With one, Flyway ignores the file without warning.
- Never edit a migration once it is merged. Add a new one instead.
- Every new table follows the record categories and the schema in the Batch 2 design document. An editable table gets the `set_audit_columns` trigger and author columns that default to `app.user_id`.
- Every migration enables and forces row-level security on the facility tables it creates, in the same file.
- When Spring Session or Spring Modulith is upgraded, compare its schema file with the copy in V1_1, and add a new migration for any change.

## Tests

| Test | What it proves |
| --- | --- |
| `FacilityServerApplicationTests` | The whole application starts against a real PostgreSQL 18, runs every migration as `ereach_owner`, and connects as `ereach_app` |
| `ModularityTests` | No module uses another module's internal classes, and no two modules depend on each other in a loop |
| `platform.FacilityIsolationTests` | TC-006-01 to TC-006-13: row-level security keeps every facility's rows apart |
| `platform.PlatformSchemaRulesTests` | TC-006-14 to TC-006-28: the facility, clinic, setting and reference tables enforce their own rules |
| `platform.TransactionContextHookTest` | TC-006-29 to TC-006-55: every transaction gets its facility and user, and nothing leaks between transactions or threads |
| `platform.AuthorColumnsTests` | TC-006-56 to TC-006-59: the database fills the author columns and protects the creator |
| `platform.SystemActorsTests` | TC-006-60: the system actor IDs are fixed |
| `platform.FacilitySettingMappingTests` | TC-006-61: a setting survives a save and a load as real JSON |

The case list, with each case's status, is in the Test Cases book. Any test that needs the database extends `PostgresIntegrationTest`. It starts one PostgreSQL container for the whole test run and creates the two users from `src/test/resources/ereach-test-roles.sql`. Tests never connect as the database superuser, because PostgreSQL does not apply row-level security to a superuser. A test that writes must run inside a facility context with a user, or the author columns refuse the 'write'.

## Where code goes

Each feature area is a package directly under `ng.ereach.facilityserver`, for example `ng.ereach.facilityserver.platform` or `ng.ereach.facilityserver.billing`. Spring Modulith treats each one as a module. Classes in the package's root are its public API; everything else goes in an `internal` sub-package that other modules may not use. Entities extend `shared`'s `BaseEntity` and follow the pattern in `docs/batch-2/system-design.md`, section 14. The full module list and the rules every change must keep are in `../ARCHITECTURE.md`.