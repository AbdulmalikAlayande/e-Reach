# e-Reach

e-Reach is an electronic health record for Nigerian hospitals and clinics. Each facility runs its own server on its own network, so registration, consultation, lab, pharmacy and billing keep working when the internet does not.

**Status: in development. Not yet for use with real patient data.**

## Who this repository is for

Developers building e-Reach. Hospital IT staff, who will install and run it, come later; their installation guide arrives with the deployment work (PRD feature F15).

## What's in it

This folder, `server/`, is the Maven build. The development database lives next to it in `../deploy/`, and the frontend will live in `../client/`.

```text
server/
├── pom.xml                    root Maven build: shared versions and the module list
├── mvnw, mvnw.cmd, .mvn/      the Maven wrapper
├── ARCHITECTURE.md            how the code is organised and the rules it must keep
├── facility-server/           the hospital application (Spring Boot)
├── shared/                    plain Java: typed IDs, Money, SystemActors
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
| `null value in column "created_by"` (SQLSTATE 23502) | A write ran with no user in the facility context, or Java sent the column as NULL | Run the work inside `FacilityContext`, and never map the author columns as writable (ARCHITECTURE.md, rule 13) |

The e-Reach Setup Guide, Chapter 11, lists every problem met during setup, with causes and fixes.

## Documentation

| Document | What it holds |
| --- | --- |
| [PRD](docs/prd.md) | What e-Reach must do: scope, roles, feature specs, engineering rules, sprint plan |
| [Batch 2: System Design](docs/batch-2/system-design.md) | Why it is built this way: decision records, diagrams, the full database schema, and how ER-006 was built |
| [Test Cases](docs/test-cases.md) | Every test case per ticket, in Given/When/Then form, with its status |
| [Setup Guide](docs/setup-guide.md) | How to set up a machine and run the server, step by step, with pitfalls |
| [ARCHITECTURE.md](ARCHITECTURE.md) | The module map and the rules every change must keep |

## Security and patient data

- Never commit real patient data. That includes test fixtures, screenshots and logs copied from a hospital. Test data must be invented.
- The passwords in `application.yml` and `deploy/` are development defaults. A hospital's server gets its passwords and keys from environment variables set by the installer.
- If you find a security problem, report it privately to the maintainer, not in a public issue. A `SECURITY.md` with a contact address will replace this note.

## Contributing and licence

e-Reach is not yet open to outside contributions. No licence has been chosen; until a `LICENSE` file is added, all rights are reserved.

Maintainer: Abdulmalik Alayande.