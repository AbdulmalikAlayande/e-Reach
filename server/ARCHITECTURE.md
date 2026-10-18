# Architecture

This file describes how e-Reach is organised and the rules that must stay true whatever changes. It is deliberately short and changes rarely. For the reasons behind each decision, read the decision records (ADR-001 to ADR-016) in the Batch 2 System Design document.

## Bird's-eye view

Every hospital runs its own **facility server**: one Spring Boot application and one PostgreSQL database on a machine inside the hospital. Staff use a browser app on the hospital network. Everything clinical and financial works with no internet.

A separate **central platform** (`central-server`, not yet built) stores encrypted backups and audit seal hashes, and publishes releases. It never writes into a facility's data. Each facility server is the only writer of its own data.

## Code map

```text
backend/facility-server/   the hospital application
backend/central-server/    the central platform (not started)
deploy/                    the development database; later the installer, Nginx, backups, UPS config
```

Inside the facility server, each top-level package under `ng.ereach` is a module. They are **planned**: none exist in code yet, and they arrive sprint by sprint.

| Module | Holds |
| --- | --- |
| `platform` | Facilities, clinics, settings, reference catalogues (ICD-10, drugs, lab tests) |
| `iam` | User accounts, practitioner profiles, memberships, roles, sign-in |
| `audit` | Audit events, sealing, verification |
| `access` | The policy engine, care relationships, break-glass, risk signals |
| `person` | Persons, identifiers, patient registrations, duplicate search |
| `consent` | Consent records |
| `visit` | Visits, queues, triage |
| `clinical` | Encounters, observations, diagnoses, allergies |
| `lab` | Lab orders, results, verification, acknowledgement |
| `prescribing` | Prescriptions |
| `pharmacy` | Stock batches, the stock ledger, dispensing |
| `payer` | Payers, plans, coverage, tariffs, benefit rules, pricing |
| `billing` | Charges, payments, receipts, refunds, cash-ups, payment clearance |
| `claims` | Claims, exports, payer responses |
| `reporting` | Read-only reports |
| `sync` | The outbox, heartbeats, seal and backup shipping |

Dependencies run one way, and `access`, `audit` and `platform` are used by almost every other module. The PRD's section 7 holds the full table of which module may call which.

## Rules that must always hold

Most of these are rules about something that must never happen. Each is enforced by the database, a test or code review.

1. **No facility can see another facility's rows.** Every facility table has `facility_id`, with row-level security enabled and forced. The policy compares it with `NULLIF(current_setting('app.facility_id', true), '')::uuid`, so a missing setting returns nothing.
2. **The application never connects as a table owner or a superuser.** It connects as `ereach_app`. Only Flyway uses `ereach_owner`. Tests follow the same rule.
3. **Nothing but a Flyway migration creates or changes a table.** Not Hibernate (`ddl-auto: validate`), not a library, not a person at a database console. A merged migration is never edited.
4. **A row never points at another facility's row.** References between facility tables include `facility_id`, because PostgreSQL skips row-level security when it checks foreign keys.
5. **No module reaches into another module's tables or `internal` package.** Modules talk only through public APIs and events. `ModularityTests` fails the build otherwise.
6. **No access decision is made outside the policy engine.** Lists are filtered in SQL before paging, never in Java afterwards.
7. **No insert-only record is ever updated or deleted.** Charges, payments, refunds, stock movements, consent decisions and audit events are corrected by adding rows.
8. **No signed clinical record is ever edited.** Encounters get addenda, prescriptions get new versions, results are superseded.
9. **No money is ever a floating-point number.** Amounts are `bigint` kobo; percentages are basis points.
10. **No time is stored without a time zone.** Instants are `timestamptz` in UTC; every "per day" rule uses Africa/Lagos days.
11. **No patient data goes into an audit event.** Events hold IDs and metadata; values only from an explicit allowlist.
12. **The central platform never writes facility data.** Every connection starts at the facility server.

## Boundaries

- **Browser to facility server:** HTTPS on the hospital network. Every write carries an idempotency key, the row version it read, a CSRF token and the facility the screen was loaded under.
- **Facility server to database:** one transaction per request. The transaction starts by setting `app.facility_id` and `app.user_id`.
- **Module to module:** a public API call within the same transaction, or an event delivered through Spring Modulith's event store (`event_publication`), which survives a restart.
- **Facility server to central platform:** outbound only, when the internet is up: heartbeats, seal hashes, backup copies and release downloads.

## Cross-cutting concerns

- **Tenancy:** the `set_config` transaction hook and row-level security, as in rules 1 and 4.
- **Audit:** a change's audit event commits in the same transaction as the change; a denial's event commits in its own transaction so it survives the rollback. Events are sealed in blocks and the seal hashes are shipped to the central platform.
- **Idempotency:** one request filter and one `idempotency_record` table make any retried write safe.
- **Identifiers:** UUIDv7, generated in the application, wrapped in a typed record per entity (`PersonId`, `FacilityId`), never a raw `UUID` in a method signature.
