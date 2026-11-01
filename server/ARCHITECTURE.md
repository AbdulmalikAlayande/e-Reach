# Architecture

This file describes how e-Reach is organised and the rules that must stay true whatever changes. It is deliberately short and changes rarely. For the reasons behind each decision, read the decision records (ADR-001 to ADR-022) in `docs/batch-2/decisions.md`, and `docs/decisions/log.md` for every decision since 27/10/2026. For how the first pieces were actually built, read section 14 of `docs/batch-2/system-design.md`, "As built in ER-006".

## Bird's-eye view

Every hospital runs its own **facility server**: one Spring Boot application and one PostgreSQL database on a machine inside the hospital. Staff use an installed desktop app on the hospital network (ADR-022). Everything clinical and financial works with no internet.

A separate **central platform** (`central-server`, not yet built) stores encrypted backups and audit seal hashes, and publishes releases. It never writes into a facility's data. Each facility server is the only writer of its own data.

## Code map

```text
eReach/
├── server/                    the Maven build; run every Maven command from here
│   ├── pom.xml                root build: shared versions and the module list
│   ├── ARCHITECTURE.md        this file
│   ├── facility-server/       the hospital application (Spring Boot)
│   ├── shared/                what both servers share: typed IDs, Money, SystemActors, BaseEntity, the error module
│   └── central-server/        the central platform (not started)
├── client/                    the SvelteKit app (not started)
├── deploy/                    the development database; later the installer, Nginx, backups, UPS config
├── docs/                      PRD, Batch 2, Test Cases, Setup Guide, decision log (the originals)
└── 2023-archive/              the 2023 code, read-only
```

Inside the facility server, each package directly under `ng.ereach.facilityserver` is a module. Classes in a module's root package are its public API; everything else goes in its `internal` package. They arrive sprint by sprint: `platform` exists (facilities, clinics, settings and the transaction hook), and `iam` has started (V3 tables, TOTP setup).

| Module | Holds |
| --- | --- |
| `platform` | Facilities, clinics, settings, reference catalogues (ICD-10, drugs, lab tests), the facility context and transaction hook |
| `iam` | User accounts, practitioner profiles, memberships, roles, sign-in, MFA, sign-in throttling |
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

1. **No facility can see another facility's rows.** Every facility table has `facility_id`, with row-level security enabled and forced. The policy compares it with `NULLIF(current_setting('app.facility_id', true), '')::uuid` in both USING and WITH CHECK, so a missing setting returns nothing and a row stamped with another facility can never be written.
2. **The application never connects as a table owner or a superuser.** It connects as `ereach_app`. Only Flyway uses `ereach_owner`. Tests follow the same rule.
3. **Nothing but a Flyway migration creates or changes a table.** Not Hibernate (`ddl-auto: validate`), not a library, not a person at a database console. A merged migration is never edited.
4. **A row never points at another facility's row.** References between facility tables include `facility_id`, because PostgreSQL skips row-level security when it checks foreign keys.
5. **No module reaches into another module's tables or `internal` package.** Modules talk only through public APIs and events. `ModularityTests` fails the build otherwise.
6. **No access decision is made outside the policy engine.** Lists are filtered in SQL before paging, never in Java afterwards.
7. **No insert-only record is ever updated or deleted.** Charges, payments, refunds, stock movements, consent decisions and audit events are corrected by adding rows.
8. **No signed clinical record is ever edited.** Encounters get addenda, prescriptions get new versions, results are superseded.
9. **No money is ever a floating-point number.** Amounts are `bigint` kobo; percentages are basis points.
10. **No time is stored without a time zone.** Instants are `timestamptz` in UTC and `Instant` in Java; every "per day" rule uses Africa/Lagos days.
11. **No patient data goes into an audit event.** Events hold IDs and metadata; values only from an explicit allowlist.
12. **The central platform never writes facility data.** Every connection starts at the facility server.
13. **No write goes in without a named author.** `created_by` and `updated_by` are NOT NULL and filled by the database from `app.user_id`; a trigger keeps `created_*` unchanged and sets `updated_*` on every update. Java never writes these columns. Work no person does runs as a fixed-ID system actor that cannot sign in (ADR-018).
14. **`shared` holds only what is truly shared.** Something goes into `shared` when both servers, or several modules, need it: typed IDs, `Money`, `SystemActors`, `BaseEntity`, the ID converters and the error module. It may use Spring and JPA (ADR-019). Anything one module needs stays in that module, including that module's problem types and exceptions.
15. **No one can lock another person out.** Failed sign-ins slow down attempts for that username from that PC; they never lock the account (ADR-007).
16. **Every error leaves as one RFC 9457 problem.** Throw `EReachException` (or a module's subclass) with a `ProblemType`; never build an error response by hand, and never put internal details or patient data in `detail` (ADR-019, PRD section 10).

## Boundaries

- **Desktop app to facility server:** HTTPS on the hospital network. Every write carries an idempotency key, the row version it read, a CSRF token and the facility the screen was loaded under.
- **Facility server to database:** one transaction per request. The transaction manager starts every transaction by setting `app.facility_id` and `app.user_id`, to empty strings when there is no context. If that fails, the transaction is rolled back and refused.
- **Module to module:** a public API call within the same transaction, or an event delivered through Spring Modulith's event store (`event_publication`), which survives a restart.
- **Facility server to central platform:** outbound only, when the internet is up: heartbeats, seal hashes, backup copies and release downloads.

## Cross-cutting concerns

- **Tenancy:** `FacilityContext` (a `ScopedValue` holding the facility and the user) and `FacilityContextTransactionManager`, which applies it to every transaction, plus row-level security, as in rules 1 and 4.
- **Authorship:** column defaults and the `set_audit_columns` trigger, as in rule 13. The base entity reads the columns back with Hibernate's `@Generated`.
- **Sign-in:** server-side sessions; Argon2id for passwords and PINs; MFA (an authenticator app, an email code, or a facility-issued security key that cannot be synced) for every staff role, at once for elevated roles and within 30 days for the rest (ADR-020, D-028); backoff per username and PC; a new session ID at sign-in, PIN unlock, facility switch and step-up.
- **Audit:** a change's audit event commits in the same transaction as the change; a denial's event commits in its own transaction so it survives the rollback. Events are sealed in blocks and the seal hashes are shipped to the central platform.
- **Idempotency:** one request filter and one `idempotency_record` table make any retried write safe.
- **Identifiers:** UUIDv7, generated in the application, wrapped in a typed record per entity and assigned in the entity's constructor, never a raw `UUID` in a method signature.
- **Errors:** `EReachException` and `ProblemType` in `shared`; `EReachExceptionHandler`, registered by auto-configuration, turns every exception into an RFC 9457 problem; each module keeps an enum of its own problems.
