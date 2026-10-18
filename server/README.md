# e-Reach

e-Reach is an electronic health record for Nigerian hospitals and clinics. Each facility runs its own server on its own network, so registration, consultation, lab, pharmacy and billing keep working when the internet does not.

**Status: in development. Not yet for use with real patient data.**

## Who this repository is for

Developers building e-Reach. Hospital IT staff, who will install and run it, come later; their installation guide arrives with the deployment work (PRD feature F15).

## What's in it

```text
eReach/
├── pom.xml                    root Maven build: shared versions and the module list
├── ARCHITECTURE.md            how the code is organised and the rules it must keep
├── backend/
│   ├── facility-server/       the hospital application (Spring Boot)
│   └── central-server/        the central platform (not started)
└── deploy/                    the development database
```

Each module has its own README: [facility-server](backend/facility-server/README.md).

## Prerequisites

- **JDK 25**, with `JAVA_HOME` pointing at it.
- **Docker Desktop**, running. The development database and the tests both run PostgreSQL in Docker.
- You do not need to install Maven. The Maven wrapper (`mvnw`, `mvnw.cmd`) downloads the right version on first use.

The development database listens on **port 5433**, not the usual 5432, so it never clashes with a PostgreSQL already installed on your machine.

## Run it locally

From the repository root, in PowerShell (on Linux or macOS, use `./mvnw` instead of `.\mvnw.cmd`):

```powershell
docker compose -f deploy/dev.yml up -d
.\mvnw.cmd -B verify
.\mvnw.cmd -pl backend/facility-server spring-boot:run
```

1. The first command starts PostgreSQL 18 on `localhost:5433` and creates the two database users, `ereach_owner` and `ereach_app`.
2. The second builds everything and runs every test against a throwaway PostgreSQL that Testcontainers starts and removes. It should end with `BUILD SUCCESS`.
3. The third starts the facility server on port 8080. On start-up, Flyway applies any new migrations as `ereach_owner`; the application then connects as `ereach_app`.

## Troubleshooting

| What you see | Why | Fix |
| --- | --- | --- |
| `password authentication failed for user "ereach_owner"` | Another PostgreSQL on your machine answered instead of the Docker one | Check that `deploy/dev.yml` maps `5433:5432` and `application.yml` uses port 5433 |
| The database users are missing after you changed `deploy/dev-init.sql` | The script only runs when the data volume is empty | `docker compose -f deploy/dev.yml down -v`, then `up -d` (this deletes the development data) |
| `permission denied for schema public` | A library tried to create its own table as `ereach_app` | Keep library schema creation switched off and add the table through a Flyway migration |

The e-Reach Setup Guide, Chapter 11, lists every problem met during setup, with causes and fixes.

## Documentation

| Document | What it holds |
| --- | --- |
| [PRD](https://claude.ai/code/artifact/c0d9ff4e-07a6-4de8-96de-4c7e3bb0efe7) | What e-Reach must do: scope, roles, feature specs, engineering rules, sprint plan |
| [Batch 2: System Design](https://claude.ai/code/artifact/a50c0f66-93e2-43d8-9d75-a77aa6ddea32) | Why it is built this way: decision records, diagrams, the full database schema |
| [Setup Guide](https://claude.ai/code/artifact/236b9582-4556-4ddb-b722-557bdd0eb877) | How to set up a machine and run the server, step by step, with pitfalls |
| [ARCHITECTURE.md](ARCHITECTURE.md) | The module map and the rules every change must keep |

The first three are Claude documents. They are private until shared, so ask the maintainer for access. They will be exported into a `docs/` folder in this repository.

## Security and patient data

- Never commit real patient data. That includes test fixtures, screenshots and logs copied from a hospital. Test data must be invented.
- The passwords in `application.yml` and `deploy/` are development defaults. A hospital's server gets its passwords and keys from environment variables set by the installer.
- If you find a security problem, report it privately to the maintainer, not in a public issue. A `SECURITY.md` with a contact address will replace this note.

## Contributing and licence

e-Reach is not yet open to outside contributions. No licence has been chosen; until a `LICENSE` file is added, all rights are reserved.

Maintainer: Abdulmalik Alayande.
