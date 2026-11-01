# Decision records

> This file in `docs/batch-2/` is the original; the earlier shared copy stopped being updated on 27/10/2026. Decisions after that date are summarised in ADR-019 to ADR-022 and listed in full, with dates, in `docs/decisions/log.md`.

Each record states one decision, why it was taken, what else was considered, and what it costs. Records marked Provisional use sensible defaults until PRD sections 16 and 17 are answered.

## Index

| ID | Decision in one line | Status | Answers |
| --- | --- | --- | --- |
| ADR-001 | One Spring Boot application, split into modules that Spring Modulith keeps apart | Accepted |  |
| ADR-002 | Each facility server is the only writer of its data; the central platform keeps backups, seals and releases | Accepted |  |
| ADR-003 | One shared schema, `facility_id` on facility tables, row-level security, composite foreign keys | Accepted |  |
| ADR-004 | A person is a bare platform ID; each facility keeps its own registration; duplicates are linked, never merged | Provisional | B2-1 |
| ADR-005 | Central owns accounts and reference data; facilities own memberships, prices and tariffs | Provisional | B2-2 |
| ADR-006 | PostgreSQL conventions: app-made UUIDv7, `timestamptz`, `bigint` kobo, partial and exclusion constraints | Accepted |  |
| ADR-007 | Server-side sessions, Argon2id, MFA for admin and clinical roles, backoff per username and PC instead of a lock, PIN unlock, new session ID at every gain in power, CSRF tokens, facility guard header | Accepted |  |
| ADR-008 | Policies are YAML data compiled to both an evaluator and SQL; request checks in memory, row checks in SQL | Accepted | B2-8 |
| ADR-009 | Audit events are insert-only and sealed in Merkle blocks; values only from an allowlist | Accepted | B2-11 |
| ADR-010 | Idempotent writes through one table and the database's own unique-key wait | Accepted | B2-9 |
| ADR-011 | Offline readiness now; device sync deferred, with its boundary fixed | Provisional | B2-10 |
| ADR-012 | Charges are immutable; status comes from linked records; reimbursement type decides what is claimed | Provisional | B2-6 |
| ADR-013 | Treatment, authorship, result responsibility, covering and break-glass are separate kinds of access | Provisional | B2-7 |
| ADR-014 | Flyway migrations run in a maintenance window; compatibility is promised per message format | Provisional | B2-3 |
| ADR-015 | pgBackRest to a second disk and the central platform; TPM unlocks the disk after a power cut | Provisional | B2-4, B2-5 |
| ADR-016 | The frontend is a SvelteKit app built to static files, served by the facility server | Accepted |  |
| ADR-017 | Money is our own small type over a whole number of kobo, with one rounding rule | Accepted | ER-004a |
| ADR-018 | The database fills the author columns from app.user\_id; jobs write as fixed-ID system actors | Accepted | B2-12 |
| ADR-019 | `shared` holds what both servers share, Spring included; errors follow RFC 9457 from one module in `shared` | Accepted | B2-14 |
| ADR-020 | Three MFA methods: authenticator app, email code, device-bound passkey on a facility security key | Accepted | B2-14 |
| ADR-021 | The installer creates the first platform operator; operator is an account flag; staff join by invitation | Accepted | B2-14 |
| ADR-022 | The client is an installed desktop app; the sign-in window loads the facility server's HTTPS address | Accepted | B2-14 |

Accepted means build it. Provisional means build it with the default described, and expect a change once the PRD's section 16 and 17 questions are answered.

## ADR-001. One application, split into enforced modules

**Status:** Accepted.

**Context.** One engineer builds e-Reach, and each hospital runs it on a single machine. The domain still has clear parts (registration, billing, claims) that must not tangle as the code grows.

**Decision.**

- The facility server is one Spring Boot application, `facility-server`. The central platform is a second, smaller one, `central-server`. Both live in one Maven build rooted at `server/pom.xml`.
- Each module is a top-level package under `ng.ereach.facilityserver`, the package of the application class (`visit`, `billing`, `claims` and so on, listed in PRD section 7). Classes in the module's root package are its public API; everything else lives in an `internal` sub-package that other modules cannot use.
- A third Maven module, `shared` (package `ng.ereach.shared`), holds plain-Java value types that both servers use: `Ids`, `PersonId`, `FacilityId`, `UserId` and `Money`. (Amended 01/11/2026 by ADR-019: `shared` may now use Spring and JPA, and also holds `SystemActors`, `BaseEntity`, the ID converters and the error module.) It sits outside `ng.ereach.facilityserver`, so Spring Modulith treats it as a library every module may use.
- A test runs `ApplicationModules.of(FacilityServerApplication.class).verify()` on every build, so a forbidden dependency or a loop fails CI.
- Modules talk in two ways only: calling another module's public API, or listening to its events. Events go through Spring Modulith's event publication registry (JDBC), so an event published inside a committed transaction is delivered even if the server restarts.
- One database, one transaction per request. A request may call several modules' APIs inside its transaction (for example `visit` raising a charge in `billing`).

**Alternatives considered.**

| Option | Why not |
| --- | --- |
| Microservices | Many processes on one hospital machine, network calls inside a consultation, and no shared transaction for a visit and its charge |
| A plain layered monolith | Nothing stops `billing` reaching into `clinical` tables; boundaries erode within months |

**Consequences.** One thing to build, deploy and back up. A module can later move into its own service because its boundary is already enforced. The cost is discipline: every cross-module need goes through a public API or an event, never a shared repository.

## ADR-002. Facility servers write; the central platform supports

**Status:** Accepted.

**Context.** Hospitals lose internet and grid power for hours. Several hospitals will use e-Reach, and each needs off-site backups and a tamper anchor for its audit trail.

**Decision.**

- Two tiers now: browsers talk to the facility server over the hospital network (HTTPS); the facility server talks to the central platform over the internet when it is up. A third tier, devices syncing to the facility server, comes in Phase 2 (ADR-011).
- The facility server is the only writer of its facility's data. The central platform never writes into a facility database.
- In the MVP the central platform does four things: stores encrypted backups, stores audit seal hashes, receives heartbeats, and publishes release bundles. It holds no live, queryable copy of clinical data.
- Everything clinical and financial works with the internet down. Payer verification, off-site backup, release downloads and account changes from the central platform wait for the connection.

**Alternatives considered.**

| Option | Why not |
| --- | --- |
| Cloud only | Care stops whenever the internet does |
| Two-way replication between facility and central | Two writers means conflicts in clinical data, which cannot be merged safely |
| Facility servers with no central platform | No off-site backup, and nothing outside the hospital to prove the audit trail was not altered |

**Consequences.** Each hospital is a server to look after (ADR-014, ADR-015). Cross-facility features (shared records, identity matching) need new central services in later phases; the MVP does not depend on them.

## ADR-003. One schema, separated by facility

**Status:** Accepted.

**Context.** Many facilities share one database design, and later one server may host a small group of clinics. A single missed filter must not expose another facility's patients.

**Decision.**

- One shared schema. Every facility-owned table has `facility_id uuid not null` and `UNIQUE (facility_id, id)`.
- Row-level security is enabled and forced on those tables. Each policy is `facility_id = NULLIF(current_setting('app.facility_id', true), '')::uuid`, which matches nothing when the setting is missing or empty.
- Flyway connects as `ereach_owner`, which owns the tables. The application connects as `ereach_app`, which does not, so RLS always applies to it.
- A transaction hook runs `SELECT set_config('app.facility_id', :facilityId, true)` at the start of every transaction. The third argument `true` makes it local to the transaction, like `SET LOCAL`, but it accepts a bind parameter, which `SET LOCAL` does not.
- PostgreSQL skips RLS when checking foreign keys, so references between facility tables are composite: `FOREIGN KEY (facility_id, visit_id) REFERENCES visit (facility_id, id)`. Clinical tables also reference `patient_registration (facility_id, person_id)`, so a clinical row cannot exist for a person not registered at that facility.
- The same hook sets app.user\_id, which only the two membership tables use, so a user can list their own memberships at sign-in before choosing a facility (schema V3, security statements part 2). Background jobs (sealer, backups, escalations) loop over facilities and set the context for each, with their own system actor as the user (ADR-018). With no context the hook still runs and sets both values to empty, so the transaction reads nothing and cannot write; if the hook itself fails, the transaction is rolled back and refused (System design, section 14).
- A schema test fails the build if any table with `facility_id` lacks RLS or has a single-column foreign key to another facility table.

**Alternatives considered.**

| Option | Why not |
| --- | --- |
| A schema per facility | Every migration runs once per facility, and cross-facility reports become unions |
| A database per facility | Same costs, and a clinic group on one server would need several databases |
| Filters in application code only | One forgotten `WHERE` leaks data; there is no second line of defence |

**Consequences.** Every database call needs a facility context, so code outside a request (jobs, tests) must set one. Reference tables are shared and read-only to the application.

## ADR-004. A person is an ID; each facility keeps its own registration

**Status:** Provisional (answers B2-1).

**Context.** The same human visits many facilities, which register them independently and offline. Reading another facility's details about a person is itself a disclosure under the NDPA.

**Decision.**

- `person` holds only an ID and creation details. It carries no name or other demographics.
- `patient_registration` holds what one facility captured: hospital number, names, sex, date of birth, contacts. One person has at most one registration per facility (`UNIQUE (facility_id, person_id)`).
- In the MVP, every new registration creates a new person. Duplicates within a facility are caught at registration (PRD F6); duplicates across facilities are expected.
- Phase 2 adds matching at the central platform. A match is recorded in `person_link` as `proposed`, becomes `confirmed` when an authorised user approves it, and can later be `reversed` with a reason. Nothing is merged or rewritten: clinical rows keep their original `person_id`, and queries follow confirmed links.
- The golden record (best-known name, sex, date of birth, phone) is derived at the central platform from linked registrations, each value tagged with its source facility and date. A facility's correction is a proposal to it, not an overwrite. Another facility reads it only with consent or break-glass.

**Alternatives considered.**

| Option | Why not |
| --- | --- |
| One shared patient record edited by every facility | Facilities overwrite each other, and every read is a cross-facility disclosure |
| Merging duplicate persons into one row | Irreversible; a wrong merge mixes two people's records |
| A patient ID that belongs to the facility | No continuity of care across facilities, ever |

**Consequences.** Until Phase 2, the same human has different `person_id`s at different facilities. Code must never assume one person equals one human across facilities.

## ADR-005. Who owns platform data while a facility is offline

**Status:** Provisional (answers B2-2).

**Context.** Some data is shared by every facility (catalogues, payer rules, staff accounts); some belongs to one hospital (its prices, its staff list). A facility must keep working when it cannot reach the central platform.

**Decision.**

| Data | Owner | MVP (one facility) | Once a second facility joins |
| --- | --- | --- | --- |
| User accounts, practitioner profiles | Central | Created on the facility server | Central creates and suspends them; facilities keep a cached copy |
| Memberships, role assignments, cover rotas | Facility | Facility admin manages them | Unchanged |
| ICD-10, drug catalogue, lab tests, frequencies, consent types, rejection reasons | Central | Shipped in the release as Flyway repeatable migrations (`R__*.sql`) | Same, plus a reference-data download between releases |
| Payers, plans, benefit rules | Central | Shipped in the release, like the catalogues | Central publishes changes; facilities apply them on the next sync |
| Clinics, services, price lists, formulary, tariffs, export profiles | Facility | Facility admin manages them | Unchanged |

- IDs are UUIDv7 everywhere, so accounts created on a facility server can later be adopted by the central platform without renumbering.
- Offline, a facility uses its cached copy. A suspension made centrally takes effect when the facility next connects; a facility admin can always suspend a membership locally, at once.
- Each facility records the version of reference data it runs, and sends it in its heartbeat.

**Alternatives considered.**

| Option | Why not |
| --- | --- |
| Each facility owns everything | Catalogues and payer rules drift apart between hospitals |
| Central owns everything | A hospital cannot add a new nurse during an internet outage |

**Consequences.** Before a second facility joins, accounts made on the first server must be adopted by the central platform; a username clash at that point is resolved by renaming one account.

## ADR-006. PostgreSQL conventions

**Status:** Accepted.

**Context.** The PRD's rules (row-level security, effective-dated prices, partial uniqueness) need database features that must be used the same way everywhere.

**Decision.**

- PostgreSQL 18 (16 is the minimum), with the `pg_trgm` and `btree_gist` extensions.
- Primary keys are `uuid`, generated in the application as UUIDv7 (`UuidCreator.getTimeOrderedEpoch()`); tables have no ID default.
- Instants are `timestamptz`; calendar dates (birth dates, effective dates) are `date`.
- Money is `bigint` kobo in columns ending `_kobo`; percentages are `integer` basis points with `CHECK (… BETWEEN 0 AND 10000)`.
- Closed lists (status, type, role) are `text` with a `CHECK (… IN (…))`, not PostgreSQL enum types, because adding a value to a `CHECK` is a one-line migration.
- Uniqueness on editable tables uses partial indexes `WHERE deleted_at IS NULL`.
- Effective-dated rows (prices, tariffs, benefit rules) use an exclusion constraint on `daterange(effective_from, effective_to, '[)')`, so two versions can never overlap in time.
- Name search uses a trigram GIN index on a normalised name column.
- Data access: Spring Data JPA for writing aggregates, with `@Version` on `row_version`; list screens use JPA Specifications (ADR-008); entities map timestamps as Instant, read the four author columns back with @Generated and never write them (ADR-018), assign their typed ID in the constructor, and each carry @SoftDelete; reports use read-only SQL through `JdbcClient`.

**Alternatives considered.**

| Option | Why not |
| --- | --- |
| MySQL (the 2023 e-Reach stack) | No row-level security and no exclusion constraints |
| PostgreSQL enum types | Changing them inside migrations is awkward and blocks expand-then-contract |

**Consequences.** Every developer needs the same PostgreSQL version locally, which Testcontainers and `deploy/dev.yml` provide.

## ADR-007. Sign-in and sessions

**Status:** Accepted.

**Context.** Staff share PCs, work 12-hour shifts, and must never act under someone else's name. Sessions must be revocable at once.

**Decision.**

- Spring Security with server-side sessions stored by Spring Session JDBC. The browser holds only a `SESSION` cookie (`HttpOnly`, `Secure`, `SameSite=Strict`).
- Passwords and PINs are hashed with `Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()`.
- MFA is required for every staff role: at once for platform operators, `facility_admin` and `data_protection_lead`, and within 30 days of getting the role for every other role (amended 01/11/2026, decision log D-028; first amended 27/10/2026, PRD B2-13). MFA is a TOTP code (RFC 6238), a code sent by email, or a passkey on a security key the facility issues (amended 01/11/2026, ADR-020). TOTP secrets are stored encrypted with AES-GCM, the key coming from the environment, and a code is accepted once only (`mfa_method.last_used_step`). A security key is stored only as its public key, in `user_security_key`.
- Failed sign-ins never lock the account. `sign_in_throttle` counts failures per username and client IP, and password-only attempts from that PC wait longer after each failure; the facility admin is alerted when one account fails from many PCs. During a backoff, the password and MFA together still sign in from that PC, in one request with one generic error and their own attempt limit, with an audit event and an admin alert (amended 27/10/2026, PRD B2-13).
- After `session.idle_lock_minutes` of inactivity the session is locked: only `POST /auth/unlock` with the owner's PIN works. Three wrong PINs end the session (assumed).
- A session expires `session.max_hours` after sign-in, whatever the activity.
- Signing in on a browser invalidates any session that browser already held, so the previous user is signed out.
- CSRF protection uses Spring Security's cookie token: the app reads `XSRF-TOKEN` and sends it back as `X-XSRF-TOKEN` on every write.
- Every write also sends `X-Facility-Context` with the facility the screen was loaded under. If it differs from the session's active facility, the request fails with 409 `FACILITY_CHANGED`.
- Step-up asks for the password (or MFA where set up) again and marks the session as stepped-up for 5 minutes (assumed).
- The session ID is replaced at every gain in power: full sign-in, PIN unlock, facility switch and a successful step-up, never on a timer. Sign-in is covered by Spring Security's default; the other three call one shared helper that calls changeSessionId. Sign-out deletes the session row.
- System accounts (user\_account.kind = 'system') can never sign in.

**Alternatives considered.**

| Option | Why not |
| --- | --- |
| JWT in browser storage | Cannot be revoked before expiry, and readable by any injected script |
| A separate identity server (for example Keycloak) | One more server per hospital, and sign-in breaks if it is down |

**Consequences.** Every request reads its session from the database; at 150 concurrent users this is well within PostgreSQL's reach, but session table cleanup must run.

## ADR-008. Access control: policies as data, compiled twice

**Status:** Accepted (answers B2-8).

**Context.** A list screen and a single-record screen must give the same answer about the same patient, and the rules must be testable and auditable as a set.

**Decision.**

- Policies are YAML files in `policies/`, read at startup by Jackson into typed `Policy` records. An unknown key or attribute stops the application from starting.
- The vocabulary is fixed and small:

| Part | Attributes | Checked |
| --- | --- | --- |
| subject | `userId`, `roles`, `cadre` | In memory, once per request |
| action, purpose | the endpoint's declared action and purpose | In memory, once per request |
| environment | `time`, `mfaDone`, `clientIp`, `riskScore`, `stepUpFresh` | In memory, once per request |
| resource | `type`, `facilityId`, `personId`, `sensitivity`, `authorUserId` | In SQL, per row |
| relationship | `treating_clinician`, `visit_assignee`, `author`, `result_responsible`, `covering`, `break_glass` | In SQL, per row |
| consent | current consent for a type, grantee and scope | In SQL, per row |

- Request conditions run first, in memory; if they fail, the answer is deny or step-up before any query runs.
- Row conditions compile to one SQL predicate per action: the allow policies joined by `OR`, then `AND NOT (…)` for each deny policy. List endpoints add it to the query as a JPA Specification before paging.
- A single-record read runs the same predicate with `AND id = :id`. List and single answers therefore come from one piece of SQL and cannot disagree.
- Creating a record (no row yet) evaluates the row conditions in memory against the values about to be written.
- Break-glass is one more relationship type, joined from active `break_glass_grant` rows; it never satisfies a role condition.
- The policy set's checksum is its version. On startup the server records it in `policy_set`, and every audit event stores it. The running code reads policies from the release, never from the database, so editing the table cannot change behaviour.

**Alternatives considered.**

| Option | Why not |
| --- | --- |
| Open Policy Agent or Cedar | A sidecar per hospital, and turning their rules into SQL for lists is partial at best |
| `if` checks in services | Not testable as a set, and lists drift from single-record checks |
| Filtering lists in Java after loading them | Breaks paging and loads data the user may not see |

**Consequences.** New kinds of rule need a vocabulary change, reviewed like any code. This refines PRD F3, which said policies are loaded into the database by migration: the database copy is now the audit record, and the release is the source.

## ADR-009. A tamper-evident audit trail sealed in blocks

**Status:** Accepted; the allowlist is provisional (answers B2-11).

**Context.** Root access to a hospital server means any table can be edited. The audit trail cannot be made immutable there, but any change to it can be made detectable.

**Decision.**

- `audit_event` is insert-only for the application role. `seq` is an identity column that orders events.
- Change events are written in the business transaction. Denials are written in a separate `REQUIRES_NEW` transaction, so they survive the request's rollback.
- An event holds IDs and metadata. Its `details` column may hold only keys on the allowlist, which the writer checks: a facility setting's key, old and new value; a role code; a membership status; a status change's from and to; a policy-set version; an export's row count. Patient data is never on the list.
- **Sealing.** Every 60 seconds, or once 500 events are waiting, a job seals each facility's unsealed events:
  1. It takes a transaction-level advisory lock for the facility, so only one sealer runs.
  2. It selects every committed event with no `audit_seal_member` row, ordered by `seq`. Events whose transactions commit late are picked up by the next seal.
  3. Each event's leaf hash is SHA-256 over a `0x00` byte and the event's canonical JSON (RFC 8785).
  4. Pairs are combined as SHA-256 over `0x01`, left and right; an odd node moves up unchanged. The final node is the Merkle root.
  5. `seal_hash` = SHA-256 over the previous seal's hash (32 zero bytes for the first), the root, the event count as 8 bytes, and `sealed_at` in microseconds as 8 bytes.
  6. The seal and one member row per event are inserted in one transaction.
- The newest seal hash is sent to the central platform when online; the acknowledgement is recorded in `audit_seal_shipment`.
- Verification (nightly and on demand) recomputes every leaf, root and seal; compares the newest shipped seal with the central copy; flags any event older than 5 minutes with no seal; and flags any event sealed more than 5 minutes after its `occurred_at`, a sign of a back-dated insert.
- Audit events are kept at least 7 years (assumed). After that, an approved disposal job running as `ereach_owner` removes whole seals and records the disposal as an event.

**Alternatives considered.**

| Option | Why not |
| --- | --- |
| One hash chain, each event pointing at the last | Every audit write waits on one row; a busy clinic slows down |
| Sending every event off-site | Needs constant internet, and puts patient metadata on the central platform |
| Claiming immutability | Not true on a server someone else controls |

**Consequences.** Tampering on the facility server is detectable, not preventable. Detection is strongest for seals already shipped; seals made during a long outage are protected only once they reach the central platform.

## ADR-010. Idempotent writes

**Status:** Accepted (answers B2-9).

**Context.** On a weak network the browser retries, and a retried payment must not be taken twice. Two copies of a request can also arrive at the same moment.

**Decision.** Every `POST` and `PATCH` carries an `Idempotency-Key` (a UUID made by the client when the user acts). Inside the request's own transaction:

1. Request-level checks run first. A deny or step-up answer returns before anything is stored, so the client retries with the same key after stepping up.
2. The server runs `INSERT INTO idempotency_record … ON CONFLICT DO NOTHING`, keyed on facility, user and key, with the operation name and a SHA-256 of the method, path and canonical body.
3. If the insert succeeded, the business work runs, the response is written into the same row, and everything commits together.
4. If another transaction holds the same key uncommitted, PostgreSQL makes this insert wait. If that transaction commits, the insert finds the row; if it rolls back, the insert succeeds and step 3 runs.
5. A found row with a different operation or hash returns 422 `IDEMPOTENCY_KEY_REUSED`. A matching row is replayed only after the user is checked again for the action; the reply carries `Idempotent-Replayed: true`.
6. A request that fails validation rolls back, leaving no row, so a retry gets the same error again.
7. Rows expire after 24 hours (assumed) and are deleted nightly.

**Alternatives considered.**

| Option | Why not |
| --- | --- |
| Storing the key after the work, in a second transaction | A crash between the two applies the work without remembering it |
| An application lock per key | Lost when the server restarts; the database already offers the wait |

**Consequences.** Every write endpoint goes through one filter and one table. Clients must create the key when the user acts, not when the request is sent.

## ADR-011. Ready for offline devices; build them later

**Status:** Provisional (answers B2-10).

**Context.** Phase 2 lets tablets work away from the hospital network. Its design must not force changes to the MVP's data, but building it now would delay the MVP by months.

**Decision.**

- The MVP keeps the data ready: UUIDv7 keys made by the application, not the database, `row_version`, `deleted_at`, an `outbox_entry` table and its writer (off until Phase 2), and idempotency keys. The facility server's clock is the only clock.
- The Phase 2 boundary is fixed now:

| Topic | Boundary |
| --- | --- |
| What a device caches | Patients with an open visit in the device user's clinic, patients seen there in the last 7 days (assumed), and reference data; never the full registry |
| What a device may create | Registrations (marked provisional), observations, allergies, encounters and their addenda, lab orders, prescriptions |
| What stays server-only | Lab results, stock, payments, claims, coverage; a device reads them but never changes them, so no money moves offline |
| Ordering | Device changes carry a hybrid logical clock; the server assigns its own sequence on receipt |
| Conflicts | The PRD research rules: clinical facts are appended, signed records amended, money and results decided by the server, demographics merged field by field with a review queue |
| Policies on a device | A TypeScript evaluator generated from the same YAML, sharing the server's test cases |
| Losing a device | Its token and key are revoked on the server; unsynced work is uploaded first if the device reconnects, then wiped |

**Alternatives considered.**

| Option | Why not |
| --- | --- |
| Build device sync in the MVP | Months of work before any hospital can use the outpatient flow |
| Decide nothing now | Phase 2 would likely need MVP tables changed |

**Consequences.** The MVP carries a little unused structure (the outbox, row versions on every table). Device features are designed against a known boundary.

## ADR-012. Charges never change; their status is derived

**Status:** Provisional (answers B2-6).

**Context.** Money must be explainable months later: what was billed, what was paid, refunded, waived, claimed or written off, and by whom.

**Decision.**

- A `charge` is inserted once and never updated. Its status is derived by the `charge_status` view from linked insert-only rows: a payment line (not voided) means paid; a zero patient share means covered; a `charge_waiver` means waived; a `charge_void` means void. Refunds reduce what was paid without changing the charge.
- `payer.PricingService` prices a charge (PRD F13); `billing` stores the result. Converting a line to cash voids the insured charge and creates a cash charge.
- Reimbursement type decides what is owed. `fee_for_service` lines become claim lines. `capitated` and `institution_funded` lines create a `utilisation_record` and no receivable. `bundled` is treated like `fee_for_service` and flagged until bundles are defined.
- `billing.isClearedForService` is the only clearance check (PRD F12).
- A visit is financially settled when every charge is paid, covered, waived or void and every claim line is paid or resolved. Clinical closure and financial settlement are independent.
- When a payer approves less than claimed, the insurance officer either bills the patient for the shortfall (a new charge linked to the original) or writes it off (`claim_writeoff`, approved by an `accounts_officer`). The default is write-off, because many schemes forbid billing enrollees (assumed).
- Payer receivable = approved minus paid, aged by submission date in 0 to 30, 31 to 60, 61 to 90 and over 90 days.
- Revenue reports separate cash collected (payments minus refunds, by payment day), charges billed (by charge day, excluding voids), payer receivables, waivers and write-offs, all on Africa/Lagos days.
- A correction found after a cash-up closes goes into the next open cash-up as a `cash_up_adjustment`.

**Alternatives considered.**

| Option | Why not |
| --- | --- |
| A status column updated in place | History is lost; nobody can say when a line became paid or who waived it |
| A full double-entry ledger | Correct but heavy for an outpatient MVP; the insert-only records can feed one later |

**Consequences.** Status queries go through a view, which needs indexes on every linked table's `charge_id`. Nothing financial is ever updated or deleted.

## ADR-013. Clinical access, kind by kind

**Status:** Provisional (answers B2-7).

**Context.** A doctor's need to see a record does not end at the same moment for every reason: finishing notes, following up a result, covering a colleague and handling an emergency each last differently.

**Decision.**

| Kind | Stored as | Starts | Ends |
| --- | --- | --- | --- |
| Treatment | `care_relationship` type `treating_clinician`, with `full_until` and `read_until` | The visit is sent to the doctor, or the doctor starts the encounter | On visit close, `full_until` = close time and `read_until` = close time + 24 hours (assumed); on handover both = now, and a new row is made for the new doctor |
| Visit assignee | `care_relationship` type `visit_assignee` | Triage | Visit close |
| Authorship | Derived from `encounter.doctor_user_id` | Encounter created | Never: the author can always read that encounter and add addenda |
| Result responsibility | `lab_order.responsible_user_id` (the ordering doctor; changed on handover) | Order placed | A `result_acknowledgement` exists for the order; covers that order's results and the encounter that ordered them |
| Covering | `cover_rota` rows (clinic, user, dates), set by the facility admin | Rota start | Rota end; equals treatment for visits opened at that clinic in that period |
| Break-glass | `break_glass_grant` | A stated reason | 60 minutes (assumed); lifts the relationship rule only, never a role rule |

- An hourly job lists abnormal verified results with no acknowledgement 24 hours after verification (assumed) on the medical director's escalation list.
- Opening a result writes `lab_result_view`; only acknowledging it ends responsibility.

**Alternatives considered.**

| Option | Why not |
| --- | --- |
| One timer for all access | Either too short for result follow-up or too long for a closed visit |
| Access to every patient for every doctor | Breaks the NDPA's purpose and minimisation principles |

**Consequences.** Policies join several sources to decide clinical access, so those joins need indexes (`care_relationship (facility_id, user_id, person_id)`, `lab_order (facility_id, responsible_user_id)`).

## ADR-014. Migrations, releases and version compatibility

**Status:** Provisional (answers B2-3).

**Context.** Many hospitals run their own servers and upgrade at different times; some stay offline for weeks.

**Decision.**

- Flyway SQL migrations named `V{n}__{description}.sql`; reference data in repeatable `R__{name}.sql`. A merged migration is never edited.
- An upgrade happens in a maintenance window chosen by the facility admin: take a backup, stop the app, run Flyway, start the new version, run the smoke checks, then let staff back in.
- Before staff write anything, rollback means restoring the pre-upgrade backup. After that, problems are fixed forward with a new release.
- Every release carries the whole migration history, so a facility several releases behind upgrades straight to the latest. A release that needs an intermediate stop (for example a data step that needs old code) is marked "required stop" in its manifest, and the installer refuses to skip it.
- Schema changes still go expand-then-contract across releases, so a release can be abandoned before its contract step runs.
- Compatibility with the central platform is promised per message format: heartbeat, seal and release manifest each carry a format version. The central platform accepts each format for at least 12 months after its successor ships (assumed), and the admin console warns 90 days before support ends (assumed).
- Releases are signed with the e-Reach release key (Ed25519). The installer checks the signature before installing anything.
- CI runs every migration on an empty database and on a copy of the previous release's schema.

**Alternatives considered.**

| Option | Why not |
| --- | --- |
| Upgrading while staff work | Needs every change to run beside two app versions, for no gain on a single-server site |
| Promising support for "the last two releases" | Says nothing about what a facility can actually send; formats are what must stay compatible |

**Consequences.** Upgrades need a short window with staff out, so releases are batched and scheduled with the hospital.

## ADR-015. Backups, off-site copies and disk keys

**Status:** Provisional (answers B2-4 and B2-5).

**Context.** A server can die, be stolen or lose power at any time. Backups must survive the hospital, and the server must come back after a power cut with nobody present.

**Decision.**

- pgBackRest writes one repository to the second disk, encrypted with `repo1-cipher-type=aes-256-cbc`: a full backup every Sunday at 02:00, a differential every other day at 02:00 (Africa/Lagos), and continuous WAL archiving with `archive_timeout = 60` seconds.
- A shipper job copies that encrypted repository to the facility's bucket in S3-compatible storage at the central platform every 15 minutes while online, retrying until it succeeds. The off-site recovery point is the time since the last successful copy, shown on the platform console.
- If the second disk passes 80% full (for example after a long outage), the platform console and the facility admin screen raise an alert.
- The data disk uses LUKS2, unlocked at boot by the TPM through `systemd-cryptenroll`, so the server starts unattended after a power cut. The recovery key is printed once, sealed in the hospital's safe, and stored in the platform secret store.
- The repository passphrase lives on the facility server and in the platform secret store. Only two named platform staff can read it there; every read is logged. Without it, a central backup cannot be restored.
- A restore drill runs monthly on a spare machine and must pass audit verification.

**Alternatives considered.**

| Option | Why not |
| --- | --- |
| A passphrase typed at every boot | Safer if the whole machine is stolen, but the hospital waits for a person after every power cut |
| Nightly `pg_dump` only | Up to 24 hours of lost work, and slow restores |
| No off-site copy | A fire or theft loses everything |

**Consequences.** If someone steals the whole running machine, TPM unlock means its disk opens; physical security of the server room matters. Platform staff who can read the passphrase could decrypt a backup, so access to it is restricted and logged.

## ADR-016. A SvelteKit app built to static files

**Status:** Accepted.

**Context.** Hospital PCs are old, and the facility server should run as little as possible. Staff want something that opens like a desktop program.

**Decision.**

- Svelte 5 with SvelteKit, built with `adapter-static` and `ssr = false`. Nginx on the facility server serves the files; the API is on the same origin under `/api`, so no CORS is needed.
- Svelte 5 runes only; the repository's `AGENTS.md` says so for AI assistants, and code review enforces it.
- TanStack Query for Svelte handles server data; Zod schemas mirror the API's request shapes.
- One API client adds `Idempotency-Key` (made when the user acts), `If-Match`, `X-XSRF-TOKEN` and `X-Facility-Context` to every write.
- (Amended 01/11/2026 by ADR-022: the app ships as an installed desktop program, not a web app manifest.) The service worker caches only the app's own files in the MVP; patient data is never cached in the browser until device sync arrives (ADR-011).
- Target: Chrome and Edge, latest two versions, at 1366 × 768 on 4 GB RAM.

**Alternatives considered.**

| Option | Why not |
| --- | --- |
| React | Workable; Svelte was chosen for smaller bundles on old PCs and the project owner's preference |
| Next.js | Needs a Node server at every hospital for rendering the app does not need |
| Electron | A second app to install and update on every PC (revisited 01/11/2026: ADR-022 accepts an installed wrapper) |

**Consequences.** Everything the screen shows comes from the API, so the backend's rules apply to every client, including future ones.

## ADR-017. Money is our own type over whole kobo

**Status:** Accepted.

**Context.** Every bill, payment, co-payment and claim passes through one Java type. Bills reach hundreds of millions of naira, so the type must be exact and must fail loudly rather than return a wrong amount. PRD F13 fixes one rounding rule for co-payments: `(amount × bps + 5000) / 10000` in whole-number division.

**Decision.**

- `Money` is a record in `shared` holding one `long`: the number of kobo. It is made with `Money.ofKobo(long)`, never from naira or a `double`.
- `plus` and `minus` use `Math.addExact` and `Math.subtractExact`, so a result past the range of a `long` throws `ArithmeticException` instead of wrapping round.
- `percentBps(int)` applies the F13 rule with `Math.multiplyExact` and `Math.addExact`. It refuses basis points outside 0 to 10,000 and a negative amount with `IllegalArgumentException`. The largest amount it always accepts is 922,337,203,685,477 kobo (about ₦9.2 trillion); anything larger is a mistake elsewhere and throws.
- A `Money` may be negative, so that corrections can be recorded, and it never changes after it is made.
- `Money` does no formatting. Screens and receipts turn kobo into "₦4,000.00".

**Alternatives considered.**

| Option | Why not |
| --- | --- |
| `BigDecimal` | Exact too, but every division needs a rounding mode chosen at the call site, so two screens could round a co-payment differently; `1.0` and `1.00` are not `equals` |
| JSR 354 with Moneta | Built for many currencies, exchange rates and local formats; e-Reach has one currency, and the F13 rule would still have to be written by us |
| A bare `long` | Exact, but a method taking `long` would accept basis points or a quantity by mistake |

**Consequences.** A long of kobo matches the `bigint` columns and the subunit amounts payment providers such as Paystack expect, so no conversion happens at any boundary. The rule lives in one method, proved by test cases TC-004-11 to TC-004-21.

## ADR-018. The database fills the author columns

**Status:** Accepted, 27/10/2026.

**Context.** Seeds, migrations, the first facility and background jobs write rows with no person behind them. With nullable `created_by`, an empty value cannot tell "the system did this" from "a bug forgot to set it", and Java code that sets the columns can forget, or disagree with the database.

**Decision.**

- `created_by` and `updated_by` are `NOT NULL` and default to `NULLIF(current_setting('app.user_id', true), '')::uuid`; `created_at` and `updated_at` are `NOT NULL DEFAULT now()`. An insert therefore sets the updated pair equal to the created pair.
- A `set_audit_columns` trigger runs before every update on editable tables: it copies `created_at` and `created_by` back from the old row and sets `updated_at` and `updated_by` from the transaction.
- Work no person does runs under `FacilityContext` with a system actor's ID: installer, reference data, audit sealer, outbox sender. Their UUIDs are fixed constants in `SystemActors` in `shared`, and V3 inserts them as `user_account` rows of kind `system`, which can never sign in. Migrations, which run with no context, give the author columns explicitly.
- The base entity maps the four columns with Hibernate's `@Generated`, so Hibernate leaves them out of its INSERT and UPDATE statements and reads back what the database set.
- `audit_event.actor_user_id` is `NOT NULL` too.

**Alternatives.**

| Option | Why not |
| --- | --- |
| Nullable author columns | A missing author looks the same as a system write, so bugs hide |
| Spring Data auditing in Java | Two writers for one column; a write outside Hibernate (JDBC, a migration) gets no author |
| One SYSTEM ID for every job | The audit trail cannot say which job did something |

**Consequences.** Any write without a user in the context fails with 23502, so tests and seeds must run inside a context. A database default applies only when a column is left out of the INSERT, so code that sends an explicit NULL fails; that is why the entity must not write these columns.

## ADR-019. What `shared` holds, and how errors are reported

**Status:** Accepted, 01/11/2026. Amends ADR-001.

**Context.** The facility server and the central server will both need the same typed IDs, base entity, converters and error format. Keeping `shared` free of Spring forced each server to copy the Spring parts.

**Decision.**

- `shared` holds whatever both servers, or several modules, share, including Spring and JPA code: typed IDs, `Money`, `SystemActors`, `BaseEntity`, the ID converters and the error module.
- Errors follow RFC 9457. Every error is `application/problem+json` with `type`, `title`, `status`, `detail`, `instance` and the extension `code`. `type` is `https://ereach.ng/problems/` plus the code in kebab case; `instance` is `urn:uuid:` plus a new UUIDv7, also written to the log. Framework errors keep `about:blank`, the status phrase as title and the code `HTTP_<status>`. Invalid fields come back together in one `VALIDATION_FAILED` problem with an `errors` list.
- `shared` holds `ProblemType`, `CommonProblem`, `EReachException`, `RecordNotFoundException`, `EReachProblems` and the Spring handler, registered through auto-configuration so any server depending on `shared` gets it. Each module keeps its own problem enum and exceptions. `iam` maps Spring Security's exceptions, because `shared` does not depend on Spring Security.
- A failed `If-Match` is 412 and a missing one 428 (RFC 9110, RFC 6585). Idempotency errors follow the IETF Idempotency-Key draft: 400, 422 and 409.

**Alternatives.**

| Option | Why not |
| --- | --- |
| Keep `shared` plain Java | Each server copies the same Spring code, and the copies drift |
| URN problem types (`urn:ereach:problem:...`) | PRD section 10 already used `https://ereach.ng/problems/...` |

**Consequences.** `shared` now pulls in Spring and JPA; a module that needs only IDs gets them too. Two items are unverified: that Spring writes extension members at the top level of the JSON under Jackson 3, and the auto-configuration registration.

## ADR-020. Three MFA methods, and passkeys that never leave their key

**Status:** Accepted, 01/11/2026. Amends ADR-007.

**Context.** Staff asked for more than one way to prove who they are. Email codes need the internet, which the facility server cannot rely on, and synced passkeys would let a credential leave the hospital.

**Decision.**

- A user may set up any of three methods, each one row in `mfa_method`: an authenticator app (TOTP), a code sent by email, or a passkey. `confirmed_at` is set when setup finishes. The 30-day clinician deadline is `user_account.mfa_required_by`.
- TOTP uses SHA-1, 6 digits and 30-second steps, accepts one step of drift and never the same step twice; its secret is encrypted with AES-256-GCM.
- Email codes are stored only as an HMAC in `mfa_challenge`, single-use and short-lived. Choosing email tells the user it needs the internet.
- Passkeys are WebAuthn credentials on facility-issued security keys, never Windows Hello, because on a shared ward PC that would prove the Windows login, not the clinician. A credential that can be synced is refused: its BE and BS flags must be false (the database checks too), attestation is required and must name a model on the facility's list, and the key's PIN is required at every use. Each key is a row in `user_security_key`. WebAuthn challenges are kept in `mfa_challenge` with the options sent.
- The relying party ID is the facility server's hostname, chosen before the first key is enrolled and never changed; it must be a name, not an IP address, served over HTTPS with a certificate the PCs trust.

**Alternatives.**

| Option | Why not |
| --- | --- |
| TOTP and hardware keys only (ADR-007 as first written) | Staff wanted email as well |
| Windows Hello passkeys | Prove the PC's login, not the person, where staff share a Windows account |
| Spring Security's built-in passkeys | Built for passkeys as the first factor, requests no attestation, no check on syncable credentials |

**Consequences.** A user who has only email cannot finish MFA while the internet is down. The WebAuthn library is not chosen yet; Yubico's `java-webauthn-server` is the suggestion.

## ADR-021. Platform operators and how staff join

**Status:** Accepted, 01/11/2026.

**Context.** The first operator must exist before any facility does, and an operator is not part of any one facility. A one-time password shown on screen does not reach someone who is not standing at the admin's desk.

**Decision.**

- The installer creates the first operator (name, username, email, optional phone, password, PIN, MFA), running as the INSTALLER system actor, only while no confirmed operator exists. It is a first-run screen in the desktop app behind a one-time setup code, and it finishes only when the operator's first MFA code is confirmed (amended 01/11/2026, decision log D-022, D-024 and D-029). Existing operators add further operators.
- Being an operator is `user_account.is_platform_operator`; system accounts can never have it; `platform_operator` leaves the facility role list. An operator with no membership sees no facility data, because row-level security needs `app.facility_id`.
- Staff, including a facility's first `facility_admin`, join by invitation: a single-use, expiring joining code, also emailed when the internet is up, whose link opens the installed app. An existing user on the same server joins from the facility switcher with the code and their PIN.

**Alternatives.**

| Option | Why not |
| --- | --- |
| `platform_operator` as a role assignment | A role assignment needs a membership, and a membership needs a facility |
| A platform role table | Only one platform role exists; a table can come when there are several |

**Consequences.** The invitation table and endpoints are designed in ER-007. Until accounts move to the central platform (B2-2), "existing user" means existing on this server only.

## ADR-022. The client is an installed desktop application

**Status:** Accepted, 01/11/2026. Amends ADR-016.

**Context.** Staff should open e-Reach like any other program, with an installer and no browser controls.

**Decision.**

- The SvelteKit build runs inside an installed desktop wrapper with its own installer. The wrapper is the developer's choice.
- The window that handles sign-in and MFA loads the facility server's HTTPS address. A UI loaded from `file://`, `app://` or `tauri.localhost` can never match the facility's relying party ID, so WebAuthn would fail there.

**Consequences.** WebAuthn with a security key must be tested inside the chosen wrapper before it is relied on. The installed app also makes registering each PC (a key pair per PC and mutual TLS) possible later; that is an open question, not a decision.
