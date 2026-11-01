# e-Reach

e-Reach is an electronic health record for Nigerian hospitals and clinics. Each facility runs its own server on its own network, so registration, consultation, lab, pharmacy and billing keep working when the internet does not.

**Status: in development. Not yet for use with real patient data.**

## Who this repository is for

Developers building e-Reach. Hospital IT staff, who will install and run it, come later; their installation guide arrives with the deployment work (PRD feature F15).

## What's in it

This folder, `server/`, is the Maven build. The development database lives next to it in `../deploy/`, the documents in `../docs/`, and the frontend will live in `../client/`.

```text
server/
├── pom.xml                    root Maven build: shared versions and the module list
├── mvnw, mvnw.cmd, .mvn/      the Maven wrapper
├── ARCHITECTURE.md            how the code is organised and the rules it must keep
├── facility-server/           the hospital application (Spring Boot)
├── shared/                    what both servers share: typed IDs, Money, SystemActors, BaseEntity, the RFC 9457 error module
└── central-server/            the central platform (not started)
```

Each module has its own README: [facility-server](facility-server/README.md), [shared](shared/README.md).

## Prerequisites

- **JDK 25**, with `JAVA_HOME` pointing at it.
- **Docker Desktop**, running. The development database and the tests both run PostgreSQL in Docker.
- You do not need to install Maven. The Maven wrapper (`mvnw`, `mvnw.cmd`) downloads the right version on first use.

The development database listens on **port 5433**, not the usual 5432, so it never clashes with a PostgreSQL already installed on your machine.

## Run it locally

From this `server/` folder, in PowerShell (on Linux or macOS, use `./mvnw` instead of `.\mvnw.cmd`):

```powershell
docker compose -f ../deploy/dev.yml up -d
.\mvnw.cmd -B verify
.\mvnw.cmd -pl facility-server spring-boot:run
```

1. The first command starts PostgreSQL 18 on `localhost:5433` and creates the two database users, `ereach_owner` and `ereach_app`.
2. The second builds everything and runs every test against a throwaway PostgreSQL that Testcontainers starts and removes. It should end with `BUILD SUCCESS`.
3. The third starts the facility server on port 8080. On start-up, Flyway applies any new migrations as `ereach_owner`; the application then connects as `ereach_app`.

## Troubleshooting

| What you see | Why | Fix |
| --- | --- | --- |
| `password authentication failed for user "ereach_owner"` | Another PostgreSQL on your machine answered instead of the Docker one | Check that `deploy/dev.yml` maps `5433:5432` and `application.yml` uses port 5433 |
| The database users are missing after you changed `deploy/dev-init.sql` | The script only runs when the data volume is empty | `docker compose -f ../deploy/dev.yml down -v`, then `up -d` (this deletes the development data) |
| `permission denied for schema public` | A library tried to create its own table as `ereach_app` | Keep library schema creation switched off and add the table through a Flyway migration |
| `Could not resolve placeholder 'ereach.mfa.encryption-key'` | The MFA secret cipher needs its key | Set `EREACH_MFA_ENCRYPTION_KEY` to a base64 32-byte key (`openssl rand -base64 32`); never commit a real one |
| `null value in column "created_by"` (SQLSTATE 23502) | A write ran with no user in the facility context, or Java sent the column as NULL | Run the work inside `FacilityContext`, and never map the author columns as writable (ARCHITECTURE.md, rule 13) |

`docs/setup-guide.md`, Chapter 11, lists every problem met during setup, with causes and fixes.

## Documentation

The documents live in `../docs/` and are the originals; the earlier shared copies of the same names stopped being updated on 27/10/2026.

| Document | What it holds |
| --- | --- |
| `docs/prd.md` | What e-Reach must do: scope, roles, feature specs, engineering rules, sprint plan |
| `docs/batch-2/` | Why it is built this way: system design with diagrams, decision records, the full database schema |
| `docs/decisions/log.md` | Every decision since 27/10/2026, with dates, including what replaced what |
| `docs/test-cases.md` | Every test case per ticket, in Given/When/Then form, with its status |
| `docs/setup-guide.md` | How to set up a machine and run the server, step by step, with pitfalls |
| `docs/pending-features.md` | The features to build next, in order |
| [ARCHITECTURE.md](ARCHITECTURE.md) | The module map and the rules every change must keep |

## Security and patient data

- Never commit real patient data. That includes test fixtures, screenshots and logs copied from a hospital. Test data must be invented.
- The passwords in `application.yml` and `deploy/` are development defaults. A hospital's server gets its passwords and keys from environment variables set by the installer.
- If you find a security problem, report it privately to the maintainer, not in a public issue. A `SECURITY.md` with a contact address will replace this note.

## Contributing and licence

e-Reach is not yet open to outside contributions. No licence has been chosen; until a `LICENSE` file is added, all rights are reserved.

Maintainer: Abdulmalik Alayande.
