# e-Reach MVP: Product Requirements

Oct 15, 2026 · @Abdulmalik Alayande

e-Reach MVP runs the outpatient department of a Nigerian general hospital, from registration to payment or insurance claim, on a server inside the hospital that keeps working when the internet is down. This document tells an engineer what to build, the rules to follow, and where to start.

**Changelog**

| Version | Date | Changes |
| --- | --- | --- |
| 1.0 | 15/10/2026 | First version |
| 1.1 | 16/10/2026 | Review comments: staff-only accounts, role permission table, `accounts_officer`, Svelte frontend, module dependency table, typed IDs, per-shift sign-in, tighter treating-clinician access, Dell test server |
| 1.2 | 16/10/2026 | External audit: break-glass never adds permissions; five kinds of clinical access; adult-only vital highlighting; reimbursement types and utilisation records; claims `exported` before `submitted`; one clearance rule; refunds and credits; composite tenant keys and missing-context rule; multi-tab facility guard; record lifecycles and audit allowlist; registration flexibility; result acknowledgement; cadre checks; visit closure split; honest backup wording; gapless receipts as a setting; Lagos day boundaries; labelled assumptions; sections 16 and 17 added |
| 1.3 | 17/10/2026 | Aligned with Batch 2 system design: set\_config context hook with app.user\_id; policies read from the release, policy\_set as audit record; append-only consent with decided\_at; lab clearance computed, not stored; prescription item status closed; charge status derived, receipt number on payment; payer shortfall default; shipper job for off-site backups; direct upgrades unless a required stop; section 17 now holds the decisions; new labelled assumptions |
| 1.4 | 17/10/2026 | After the first working setup: rule 2 no longer contradicts rule 1 (clinical rows reference the registration); row\_version only on editable tables; audit retention matches the disposal rule; Flyway is the only table creator and library self-initialisation is off; tests never connect as the superuser; repository layout, local run instructions, tech stack (database users, local database on port 5433) and F2 and F9 table lists updated; Sprint 1 ticket status added; glossary adds migration and the two database users; links to the Batch 2 doc and the Setup Guide |

## 1. Read this first

Read sections 1 to 10 once; they take about 40 minutes and give you everything shared across features. Then open the feature spec (F1 to F15) for your ticket. Each spec is written to stand alone: what the user does, the rules, the acceptance tests, the endpoints and the tables.

| If you want to know | Go to |
| --- | --- |
| Why this product exists | 2. The problem |
| What is in and out of the MVP | 3. What the MVP is |
| Who the users are and what they call things | 4. Roles, 6. Words we use |
| How a patient moves through the hospital | 5. The outpatient journey |
| Which tools, folders and conventions to use | 7. Tech stack, 8. Engineering rules, 10. API conventions |
| Which tables exist | 9. Main entities |
| What to build for a ticket | 11. Feature specs F1 to F15 |
| What to pick up first | 13. Build order and your first tickets |
| When a ticket is finished | 14. Definition of done |
| Why the system is built this way, and the full database schema | Batch 2: System Design (linked below this table) |
| How to set up a machine and run the server | e-Reach Setup Guide (linked below this table) |

Companion documents: e-Reach Batch 2: System Design for the decision records, diagrams and SQL schema; e-Reach Setup Guide for setting up and running the project.

Three working rules apply to everyone:

1. If this document and the code disagree, this document wins until someone updates it in the same pull request.
2. If this document is silent on something your ticket needs, ask, then add the answer here before you merge.
3. Every assumption in section 15 carries one of three labels. **Low-risk:** build as documented. **Validate before patients:** build it, but keep it switched off until a clinician or the hospital signs it off. **Legal:** needs legal review before it is enabled. Anything marked **(assumed)** in the text is listed there.

## 2. The problem, told as one patient's morning

Mrs Bisi Adeyemi is 54, has high blood pressure, and is enrolled on her state health insurance scheme. She reaches a general hospital in Lagos at 7:10 am.

1. **Records cannot find her folder.** She was last seen eight months ago. After 40 minutes of searching, the records officer opens a new folder with a new hospital number. Her history now lives in two places.
2. **She waits.** Studies of Nigerian outpatient clinics found most patients waiting 90 to 180 minutes, often for under 5 minutes with the doctor ([UDUTH study](https://pmc.ncbi.nlm.nih.gov/articles/PMC3868129)). Mrs Adeyemi sees the doctor at 9:50.
3. **The doctor works blind.** He cannot see which blood pressure drug she already takes from another clinic, or that she reacted badly to a sulfa drug last year. He prescribes a drug she is already on.
4. **Money leaks.** Her lab test is done before anyone checks whether the scheme covers it. The scheme later rejects the claim because the diagnosis code and the doctor's licence number are missing. The hospital absorbs the cost.
5. **Nobody can say who saw her record.** Her folder passed through six pairs of hands, and none of it was recorded.

### What should happen instead

The records officer types her phone number and finds her in two seconds. The nurse records her vitals into the same record. The doctor opens the visit and sees her allergies, current drugs and last results at the top of the screen, and the system warns him before he prescribes a duplicate drug. The cashier sees exactly what the scheme covers and what she pays. The claim leaves with every field the scheme needs. Every look at her record is logged and sealed. All of this works even when the internet is down.

That is the MVP.

## 3. What the MVP is, and what it is not

The MVP is the outpatient department of one hospital, running on a server in that hospital, used through a web browser on the hospital's local network. The data model is built for many hospitals from day one, but the pilot runs in one.

### The exit test

The MVP is done when this works in the pilot hospital with the internet cable unplugged:

1. A records officer finds an existing patient, or registers a new one without creating a duplicate.
2. A nurse records vitals and sets a triage priority.
3. A doctor documents the consultation, records an ICD-10 diagnosis, orders a lab test and prescribes.
4. The cashier collects payment, or the insurance desk applies the patient's cover.
5. The lab records the result and the doctor sees it.
6. The pharmacist dispenses from tracked stock.
7. An insurance claim line is produced with every field the payer needs.
8. Every step was allowed by a written policy and recorded in a sealed audit trail.

### In the MVP

| Area | Included |
| --- | --- |
| Platform | Many facilities in one database design, facility server in the hospital, browser client, backups and audit seals sent to a central server |
| People and access | Users who can work in several facilities, roles, sign-in with idle lock, layered access control, break-glass, consent records, sealed audit trail |
| Clinical | Patient registration and search with duplicate check, visits and queues, triage and vitals, consultation notes, ICD-10 diagnoses, allergies, lab orders and results, prescriptions |
| Pharmacy | Stock received in batches, dispensing by earliest expiry, stock as a ledger, low-stock and expiry lists |
| Money | Facility price list, charges, payments, receipts, daily cash-up, insurance cover and co-payment, claims and claim export files |
| Reports | Daily outpatient register, attendance, top diagnoses, revenue, claims, stock |

### Not in the MVP

Inpatient wards, theatre, referrals between hospitals, maternal and child health registers, appointments and SMS reminders, tablets or phones working away from the hospital network, live connections to insurer systems, DHIS2 monthly returns, FHIR APIs, a patient portal, wallets or instalment payments, AI features. Do not build any of these, even partly, unless a ticket says so. The data model leaves room for them.

## 4. Who uses e-Reach

A user is a staff member who signs in. Each user has one account and can hold several roles. Roles are given per facility, so a locum doctor can be a doctor at two hospitals with one account. The role code is what appears in code and policies.

| Role code | Who | Main screens |
| --- | --- | --- |
| `records_officer` | Health records staff at the front desk | Patient search, Registration, Open visit |
| `nurse` | Triage and clinic nurses, midwives | Triage queue, Vitals |
| `doctor` | House officers, medical officers, residents, consultants | Doctor queue, Consultation |
| `lab_scientist` | Laboratory scientists and technicians | Lab worklist, Result entry |
| `pharmacist` | Pharmacists and pharmacy technicians | Pharmacy queue, Dispense, Stock |
| `cashier` | Cash office | Cashier, Receipts, Cash-up |
| `accounts_officer` | Head of accounts or cash office supervisor | Voids, Cash-up review, Revenue reports |
| `insurance_officer` | NHIA, HMO and state scheme desk | Coverage, Claims |
| `facility_admin` | Hospital IT or admin lead | Admin |
| `medical_director` | Medical director or management | Reports, Waivers |
| `data_protection_lead` | Person responsible for privacy | Access review, Audit |
| `platform_operator` | e-Reach team | Platform console |

### What each role can and cannot do

Policies grant exactly these permissions and nothing more. "Patient header" means name, hospital number, age, sex, payment mode and allergies; it never includes notes, diagnoses or results.

| Role | Can | Cannot |
| --- | --- | --- |
| `records_officer` | Search patients; register and edit demographics; record identifiers; record consent; open, cancel or mark visits as left without being seen | Read notes, diagnoses, results or prescriptions; touch money |
| `nurse` | Search patients; read the patient header and visit; record vitals and allergies; set triage priority; send visits to a doctor; break-glass in an emergency | Diagnose, order, prescribe or sign encounters; touch money |
| `doctor` | Everything clinical for patients in their care: read history, write and sign encounters, diagnose, record allergies, order lab tests, prescribe and amend, close visits; break-glass in an emergency | Dispense, enter lab results, take payments, change prices |
| `lab_scientist` | See the lab worklist with patient header; record collection; enter, verify and supersede results; cancel uncollected orders | Search patients; read notes or other departments' data |
| `pharmacist` | See the pharmacy queue with patient header and allergies; quote; dispense; receive and adjust stock; read stock reports | Prescribe or change prescriptions; read notes; take payments |
| `cashier` | Search patients; see charges; take payments; print receipts; run and close their own cash-up | Void payments; waive charges; read any clinical data |
| `accounts_officer` | Void payments the same day with a reason; review and close any cashier's cash-up; read revenue reports | Take payments for patients; read clinical data |
| `insurance_officer` | Search patients; record and verify coverage; enter pre-authorisation codes; convert lines to cash; build, export and track claims; record payer responses | Change tariffs or benefit rules; read notes beyond what a claim needs |
| `facility_admin` | Manage users, memberships and roles; clinics; settings; price lists; formulary; tariffs; export profiles | Read any clinical record; take, void or waive payments |
| `medical_director` | Read all reports; approve waivers | Read individual clinical records unless also holding the `doctor` role with a care relationship |
| `data_protection_lead` | Read the audit trail; review break-glass; run audit verification; approve patient-level exports | Change clinical, financial or audit data |
| `platform_operator` | Create facilities; see server health, backups and seals; publish releases | Read any facility's patient data |

A person who needs two jobs gets two roles, for example `doctor` and `medical_director`. Nobody gets a permission by being an admin.

Only staff are users and only staff sign in. Patients do not log in to the MVP: their data is fully in the system, but a patient portal needs an internet-facing server, SMS verification and NDPA access-request handling, so it comes in Phase 3. Every rule about usernames, passwords, PINs and lockouts in this document applies to staff accounts only.

## 5. The outpatient journey

&#91;embedded content: outpatient journey · 5 stations, 1 payment gate\]

A patient moves through five stations. The cashier desk is the gate: by default the lab will not collect a specimen and the pharmacy will not dispense until the charge is paid or covered by insurance. The consultation fee can also be gated before triage, but that setting is off by default.

## 6. Words we use

Use these words exactly, in code, tickets and conversation. Most bugs in health software start with two people meaning different things by "patient" or "visit".

| Term | Meaning in e-Reach |
| --- | --- |
| Facility | One hospital, clinic or PHC. Most data belongs to exactly one facility. |
| Facility server | The machine inside the hospital that runs e-Reach and its database. It is the only writer for that facility's data. |
| Central platform | The e-Reach server in a Nigerian data centre. In the MVP it stores backups and audit seals and distributes releases. |
| Person | A patient: any human whose health record e-Reach keeps, across all facilities. One row per human, ideally. Staff are not persons in this sense; they are users. |
| Registration | A person's patient record at one facility: their hospital number and the details that facility captured. This is the Patient entity. |
| Hospital number | The number a facility gives a registered person, for example `GH/2026/000123`. Unique within the facility only. A person has one per facility that registered them, never two at the same facility. |
| Visit | One trip to the hospital, from opening at records to closing. Holds the queue position and how the visit is paid for. |
| Encounter | The doctor's consultation inside a visit: notes, diagnoses, orders, prescriptions. |
| Observation | One measured value, such as a temperature or blood pressure reading. |
| Care relationship | A record saying a user is caring for a person right now, for example "Dr Musa is treating Mrs Adeyemi on this visit". Clinical access depends on it. |
| Break-glass | Opening a record without a care relationship in an emergency, with a stated reason that is reviewed later. |
| Policy | A rule, written in a YAML file, that allows or denies an action. The only place access decisions are made. |
| Purpose | Why data is being used: treatment, payment, claims, audit. Each screen declares its purpose; users are not asked. |
| Consent record | What a person agreed to, for which purpose, with whom, and when it was given or withdrawn. |
| Audit event | One row saying who did what to which record, when, why and whether it was allowed. |
| Audit seal | A hash that locks a group of audit events. Any later change to a sealed event is detectable. |
| Payer | Whoever pays besides the patient: NHIA, an HMO, a state scheme such as Ilera Eko, or a government free-care programme. |
| Coverage | A person's membership of a payer plan, with member number and validity dates. |
| Tariff | The price a payer has agreed to pay this facility for a service, valid between two dates. |
| Benefit rule | Whether a plan covers a service and how much the patient pays (the co-payment). |
| Charge | One billable line: a service, its quantity and amount, split into patient share and payer share. |
| Claim | A request to a payer to pay the payer share of a visit's charges. |
| Kobo | One hundredth of a naira. All money is stored as whole kobo. ₦5,000 is stored as `500000`. |
| UUIDv7 | The ID format for every row. It sorts by creation time, which keeps database indexes fast. |
| Idempotency key | A random ID the client sends with each write so that a retried request is not applied twice. |
| Soft delete | Marking a row deleted by setting `deleted_at` instead of removing it. |
| Row version | A number that goes up on every update, used to stop two people silently overwriting each other. |
| Migration | A numbered SQL file that Flyway runs once, in order, to change the database. The only way tables are created or altered. |
| ereach\_owner, ereach\_app | The two database users. The owner creates tables and is used only by Flyway; the application connects as ereach\_app, which owns nothing, so row-level security always applies to it. |

## 7. Tech stack and repository

The backend is one Spring Boot application split into modules with enforced boundaries (a modular monolith). The frontend is a Svelte 5 app built with SvelteKit into static files and served by the facility server. Staff install it from Chrome ("Install app"), so it opens in its own window like a desktop program, without Electron. SvelteKit's own server features are not used: Spring Boot is the only backend, and any future client calls the same API. Exact versions are pinned in the build files in Sprint 1; use the newest stable release of each at that time.

| Layer | Choice | Notes |
| --- | --- | --- |
| Language, backend | Java 25 (LTS) |  |
| Framework | Spring Boot 4.1 with Spring Modulith 2.1 | Modulith checks that modules only talk through their public APIs. Spring Boot 3.x left open-source support in June 2026, so the project starts on 4.x (Spring Framework 7, Jackson 3) |
| Build | Maven, run through the Maven wrapper | One multi-module build from the repository root, modules under `backend/` |
| Database | PostgreSQL 18 (16 is the minimum) | Extensions: `pg_trgm` for name search, `btree_gist` for date-range rules |
| Database users | ereach\_owner and ereach\_app | The owner creates tables (Flyway only); the application connects as ereach\_app, which owns nothing, so row-level security always applies to it |
| Local database | PostgreSQL 18 in Docker Compose (deploy/dev.yml) | Reached on port 5433 so it never clashes with a PostgreSQL already installed on 5432 |
| Migrations | Flyway | SQL files only, in `backend/facility-server/src/main/resources/db/migration` |
| Sessions | Spring Session with JDBC | Server-side sessions in PostgreSQL, so they can be revoked |
| Frontend | Svelte 5 with TypeScript, SvelteKit with the static adapter | Installable as an app window; small bundles for old PCs; Svelte 5 runes only, enforced by the repository's AGENTS.md |
| Frontend data | TanStack Query for Svelte, Zod | Zod schemas mirror API request shapes |
| Tests, backend | JUnit Jupiter (version managed by Spring Boot), Testcontainers 2 with real PostgreSQL 18, Spring MockMvc | No in-memory database substitutes; tests connect as ereach\_app, never as the superuser |
| Tests, frontend | Vitest, Svelte Testing Library, Playwright for end-to-end |  |
| Facility server | Ubuntu Server LTS, Docker Compose, Nginx for TLS and static files |  |
| Backups | pgBackRest | Local second disk plus encrypted copy to the central platform |
| Power | UPS with Network UPS Tools (NUT) | Triggers a clean shutdown on low battery |
| Central platform | Same repository and Maven build, separate `central-server` application, hosted at MTN's Lagos data centre (assumed) |  |

### Repository layout

```text
eReach/
├── pom.xml                      root build: shared versions, the module list, no dependencies of its own
├── mvnw, mvnw.cmd, .mvn/        Maven wrapper (pins the Maven version)
├── .gitignore, .gitattributes   one set for the whole repository
├── AGENTS.md                    rules for AI coding assistants (Svelte 5 runes only, this PRD's section 8)
├── archive/                     2023 code, read-only (git tag v1-2023)
├── backend/
│   ├── facility-server/         the hospital application (child pom.xml)
│   │   ├── src/main/java/ng/ereach/
│   │   │   ├── platform/        facilities, clinics, settings, reference data (ICD-10, drugs, lab tests)
│   │   │   ├── iam/             users, practitioner profiles, memberships, roles, sign-in
│   │   │   ├── audit/           audit events, sealing, verification
│   │   │   ├── access/          policy engine, care relationships, break-glass, risk
│   │   │   ├── person/          persons, identifiers, patient registrations, duplicate search
│   │   │   ├── consent/         consent records
│   │   │   ├── visit/           visits, queues, triage
│   │   │   ├── clinical/        encounters, observations, diagnoses, allergies
│   │   │   ├── lab/             lab orders, specimens, results
│   │   │   ├── prescribing/     prescriptions, formulary search
│   │   │   ├── pharmacy/        stock batches, stock ledger, dispensing
│   │   │   ├── payer/           payers, plans, coverage, tariffs, benefit rules, charge pricing
│   │   │   ├── billing/         price lists, charges, payments, receipts, cash-ups
│   │   │   ├── claims/          claims, claim lines, exports, payer responses
│   │   │   ├── reporting/       read-only reports
│   │   │   └── sync/            outbox, heartbeat, seal and backup uplink
│   │   ├── src/main/resources/
│   │   │   ├── application.yml
│   │   │   ├── db/migration/    Flyway SQL: V1 extensions, V1_1 framework tables, then the Batch 2 schema
│   │   │   └── policies/        access policies in YAML
│   │   └── src/test/
│   │       ├── java/ng/ereach/  PostgresIntegrationTest base class, ModularityTests
│   │       └── resources/       ereach-test-roles.sql
│   ├── central-server/          the central platform application (joins <modules> when it has a pom.xml)
│   ├── shared/                  added with ER-004: typed IDs, Money, error format (production code only)
│   └── shared-test/             added when a second module needs the test base classes (test scope only)
├── frontend/                    SvelteKit app (ER-002)
├── deploy/
│   ├── dev.yml                  development PostgreSQL 18, port 5433
│   ├── dev-init.sql             creates ereach_owner and ereach_app
│   └── ...                      later: install scripts, Nginx, pgBackRest, NUT config
└── docs/                        this PRD, ADRs, diagrams
```

### Which module may call which

Dependencies point one way only. Spring Modulith fails the build if a module calls one not listed here, or if any chain of calls or events loops back on itself. Every module may also use `shared`, read reference data from `platform`, write through `audit`, and ask `access` for decisions; that is why `access` itself calls nobody.

| Module | May call | Listens to events from |
| --- | --- | --- |
| `platform`, `audit`, `person` | nothing else |  |
| `access` | nothing else; it defines small lookup interfaces (`SubjectLookup`, `ConsentLookup`) that `iam` and `consent` implement |  |
| `iam` | `access` (implements `SubjectLookup`) |  |
| `consent` | `person`, `access` (implements `ConsentLookup`) |  |
| `payer` | `person` |  |
| `billing` | `payer` (to price a charge) |  |
| `visit` | `person`, `payer` (coverage check), `billing` (raise card and consultation charges), `access` (grant and end care relationships) |  |
| `clinical` | `visit` (status changes), `person` |  |
| `lab` | `clinical`, `visit`, `billing` (raise charges), `access` (grant access to a late result) | `billing` (charge cleared) |
| `prescribing` | `clinical` |  |
| `pharmacy` | `prescribing`, clinical (allergies), `visit`, `billing` (raise charges) | `billing` (charge cleared) |
| `claims` | `billing`, `payer`, `clinical`, `consent`, `iam` (licence numbers) | `visit` (visit closed) |
| `reporting` | read-only views of every module; nothing calls `reporting` |  |
| `sync` | `audit` |  |

Two patterns keep this free of loops. Callers pass what a module needs instead of the module looking back (`visit` gives `billing` the payment mode and coverage, so `billing` never reads `visit`). And `access` defines interfaces that other modules implement, so it can read roles and consent without depending on `iam` or `consent`. Claims sit in their own module for the same reason: `billing` asks `payer` for prices, and nothing in `payer` needs `billing`.

### Running it locally

You need Docker Desktop, JDK 25 and, once the frontend exists, Node.js LTS. From the repository root, `docker compose -f deploy/dev.yml up -d` starts PostgreSQL 18 on port 5433 (not 5432, so it never clashes with a PostgreSQL already installed on your machine). `.\mvnw.cmd -pl backend/facility-server spring-boot:run` on Windows, or `./mvnw ...` elsewhere, starts the backend, which runs the Flyway migrations as `ereach_owner` and then connects as `ereach_app`. `.\mvnw.cmd -B verify` builds and runs every test against a throwaway PostgreSQL started by Testcontainers. Loading a demo facility and one user per role comes with ER-003 once the facility and user tables exist; `npm --prefix frontend run dev` will start the frontend once ER-002 adds it. The e-Reach Setup Guide explains every file involved and the problems met while setting it up.

## 8. Engineering rules every change must follow

These rules exist because each one prevents a specific, expensive mistake. Code review rejects any change that breaks one, so learn the why, not just the what.

1. **Every facility-scoped table has a `facility_id` column, PostgreSQL row-level security (RLS) is on, and references between facility tables include the facility.** Why: a bug in one query must never show hospital A's patients to hospital B. How: at the start of every request transaction the backend runs `SELECT set_config('app.facility_id', :facilityId, true)` (and the same for `app.user_id`), and each table has the policy `facility_id = NULLIF(current_setting('app.facility_id', true), '')::uuid`, which returns no rows when the setting is missing or empty. The third argument `true` makes the setting end with the transaction, like `SET LOCAL`, so a pooled connection never carries the last request's facility; unlike `SET LOCAL`, it accepts a bind parameter. Only the two membership tables read `app.user_id`, so a user can list their own memberships at sign-in. Because PostgreSQL foreign-key checks bypass RLS, child tables reference parents by composite key, for example `(facility_id, visit_id)` referencing `visit (facility_id, id)`, so a facility-A row can never point at a facility-B record. Clinical rows also reference `patient_registration (facility_id, person_id)`, so the database refuses clinical data for someone not registered at that facility.
2. **A person is not owned by a facility.** Why: the same human visits many hospitals. How: `person` has no `facility_id`; `patient_registration` links a person to a facility; clinical tables carry both `person_id` and `facility_id` and reference `patient_registration (facility_id, person_id)`, so a clinical row cannot exist for someone not registered at that facility (rule 1).
3. **A user is not owned by a facility either.** How: `user_account` has no `facility_id`; `facility_membership` links a user to a facility; `role_assignment` rows hang off the membership, one row per role.
4. **Primary keys are UUIDv7, generated in the application.** Why: they can be created on any machine without asking the database, which device sync will need, and they sort by time. How: use `Ids.newId()` from `shared`; never use database sequences for primary keys. In Java every ID has its own record type (PersonId, FacilityId, UserId and so on, each wrapping a UUID), so the compiler refuses a FacilityId where a PersonId is expected. Raw UUID or String IDs never appear in method signatures.
5. **Every write request carries an `Idempotency-Key` header with a random UUID created by the client when the user acts.** Why: on a weak network the browser may retry, and a retried payment must not be taken twice. How: the server stores the key with the response; a repeat returns the stored response. Never build the key from timestamps or form fields. Keys are scoped to the user, facility and operation; the same key with a different body is refused with 422; a second request arriving while the first is still running waits for its result; the stored response and the business change commit together. Batch 2 defines expiry and step-up handling.
6. **Updates use the row version.** Why: two people editing the same record must not silently overwrite each other. How: every editable table has `row_version bigint`; clients send it in the `If-Match` header; a mismatch returns HTTP 412 and the client reloads.
7. **Each record follows the lifecycle of its category (table below), never one rule for all.** Editable records are soft-deleted with `deleted_at`, and their unique indexes are partial: `CREATE UNIQUE INDEX ... WHERE deleted_at IS NULL`, so a deleted row does not block a new one with the same value. Records are kept for their retention period and then disposed of by an approved, audited process; "never delete" must not quietly become "keep forever".
8. **Money is a `bigint` of kobo.** Why: floating point turns ₦0.10 + ₦0.20 into ₦0.30000000000000004. How: columns end in `_kobo`, Java uses the `Money` type from `shared` backed by `long`, JSON sends integers. Percentages are basis points (1% = 100). This is how Paystack and Flutterwave take amounts too. We use one currency, so a small Money record is enough; the JSR 354 Money API (Moneta) is not needed.
9. **Time is stored as `timestamptz` in UTC and shown in Africa/Lagos.** The facility server's clock is the only clock that orders events; never trust a time sent by the browser. Every "per day" rule (cash-ups, the daily register, receipt years, "visits still open at midnight") uses Africa/Lagos day boundaries, never UTC, which would cut the day at 1 am.
10. **Access decisions come only from policies.** Why: scattered `if (user.isAdmin())` checks cannot be audited or tested as a set. How: an endpoint declares its action and purpose; the policy engine decides. List queries get the policy as a SQL condition before pagination, never filtered in Java afterwards. In practice the policy compiler produces a Spring Data JPA Specification that is combined with the screen's own filters, so the database does the filtering. Return a Slice, not a Page, so no count query runs on every request.
11. **Every data change writes its audit event in the same transaction, and every denied request writes its denial in a separate one so it survives the rollback.** Audit events hold IDs and metadata, never clinical text; the only values they may hold are those on an explicit allowlist (for example a facility setting's old and new value), and patient data is never on it. The audit table is insert-only for the application.
12. **The audit log and the sync outbox are different tables with different lives.** Audit events are kept for at least 7 years (assumed) and removed only by the approved, audited disposal process in rule 7. Outbox entries are a delivery queue and are removed after confirmed delivery. The queue is a PostgreSQL table read by a background worker with SELECT ... FOR UPDATE SKIP LOCKED. No RabbitMQ, Redis or BullMQ: the entry must commit in the same transaction as the data it describes, which only a table in the same database can do.
13. **Signed clinical records are never edited.** A signed encounter gets an addendum; a signed prescription gets an amendment that creates a new version.
14. **Stock is a ledger.** Every receipt, dispense and adjustment is a new row; the balance is derived. Think of a bank statement: each line is money in or out, and the balance is the sum of the lines, so you can always explain why the shelf holds 40 tablets. Never update a quantity in place without writing the movement in the same transaction.
15. **Prices that change over time are versioned with effective dates, and anything billed records the version it used.** A tariff change next month must not change last month's claims.
16. **The schema changes only through Flyway, in expand-then-contract steps.** Add the new column, ship code that writes both, migrate, then remove the old one in a later release. Never edit a migration that has been merged. Flyway, connected as ereach\_owner, is the only thing that creates or alters tables: Hibernate runs with ddl-auto: validate, and libraries that would create their own tables (Spring Session, Spring Modulith) have that switched off. Their tables are copied unchanged from the library's own schema file into a migration, as described in the e-Reach Setup Guide, Chapter 8.
17. **No secrets in the repository.** A hospital's server gets its passwords and keys from environment variables set by the installer; the dev defaults in application.yml and `deploy/dev.yml` are for local use only.
18. **Every feature ships with tests at three levels:** unit tests for rules, integration tests against real PostgreSQL through Testcontainers, and API tests through MockMvc. Every policy file ships with tests for at least one allow and one deny. Integration tests connect exactly as production does: the application as ereach\_app and Flyway as ereach\_owner. They never connect as the database superuser (so no @ServiceConnection), because PostgreSQL does not apply row-level security to a superuser and tenancy tests would pass without proving anything.

### Record lifecycles (rule 7)

| Category | Examples | How it changes |
| --- | --- | --- |
| Editable operational records | Registrations, settings, clinics, formulary, coverage, claims (status), cash-ups (until closed) | Versioned updates with `If-Match`; soft delete |
| Signed clinical records | Signed encounters, signed prescriptions, verified results | Never edited; addenda, amendments or superseding rows |
| Financial and stock transactions | Charges and their pre-authorisations, waivers and voids; payments and payment voids; refunds, credits, cash-up adjustments; claim lines, responses and write-offs; stock movements | Insert-only; mistakes fixed by compensating entries; a charge's status is derived from the rows linked to it |
| Consent history | Consent records | Append-only; the newest decision is current |
| Audit records | Audit events, seals, seal members, shipments | Insert-only and sealed |
| Transport queue | Outbox entries, idempotency records | Deleted after delivery or expiry |

## 9. Main entities at a glance

This is the map, not the full schema; the full columns, types and indexes come in the database schema document (Sprint 0, Batch 2). Scope tells you whether the table has `facility_id`.

Every table, except insert-only tables (audit records, financial and stock transactions, consent history; see the record lifecycles in section 8), which have id, created\_at and created\_by but no update or delete columns, also has: `id uuid` (UUIDv7), `created_at`, `created_by`, `updated_at`, `updated_by`, `row_version bigint`, `deleted_at` (nullable). Facility-scoped tables add `facility_id uuid not null` with RLS on.

| Table | Scope | What one row is | Key fields |
| --- | --- | --- | --- |
| `facility` | Platform | A hospital, clinic or PHC | name, type (phc, clinic, secondary, tertiary), state, lga, hospital\_number\_prefix |
| `facility_setting` | Facility | One setting value | key, value (for example `gate.lab_requires_payment`) |
| `user_account` | Platform | One login | username, full\_name, phone, password\_hash, mfa\_secret, status |
| `practitioner_profile` | Platform | A clinician's credentials | user\_id, cadre, licence\_body, licence\_number, specialty |
| `facility_membership` | Facility | A user working at a facility | user\_id, department, status, valid\_from, valid\_to |
| `role_assignment` | Facility | One role on a membership | membership\_id, role\_code |
| `person` | Platform | A human being | (identity anchor only; demographics live on registrations) |
| `person_identifier` | Facility | A phone, NIN or other identifier as captured | person\_id, type, value\_normalised, value\_hash, value\_encrypted |
| `patient_registration` | Facility | A person registered at this facility | person\_id, hospital\_number, names, sex, date\_of\_birth, dob\_estimated, address, next\_of\_kin |
| `consent_record` | Facility | One consent given or withdrawn | person\_id, consent\_type, grantee\_facility\_id, purpose, scope, version, status, decided\_at (append-only: each decision is a new row) |
| `care_relationship` | Facility | A user caring for a person now | person\_id, user\_id, type, visit\_id, starts\_at, ends\_at, status |
| `break_glass_grant` | Facility | An emergency access window | person\_id, user\_id, reason\_code, reason\_text, expires\_at, reviewed\_by, reviewed\_at |
| `visit` | Facility | One hospital trip | person\_id, clinic\_id, visit\_type, payment\_mode, coverage\_id, status, priority, opened\_at, closed\_at |
| `encounter` | Facility | A doctor's consultation | visit\_id, person\_id, doctor\_user\_id, complaint, history, examination, plan, disposition, signed\_at |
| `observation` | Facility | One measured value | person\_id, visit\_id, code (temperature, pulse...), value\_numeric, unit, recorded\_at |
| `condition` | Facility | One diagnosis | encounter\_id, person\_id, icd10\_code, status (provisional, confirmed) |
| `allergy` | Facility | A known allergy | person\_id, substance, reaction, severity |
| `lab_order` / `lab_result` | Facility | A test ordered and its results | order: encounter\_id, lab\_test\_code, priority, status; result: analyte, value, unit, ref\_low, ref\_high, abnormal\_flag |
| `prescription` / `prescription_item` | Facility | A signed set of drugs | prescription: encounter\_id, version, previous\_version\_id, status, signed\_at; item: drug\_id, dose, route, frequency, duration\_days, quantity |
| `stock_batch` / `stock_movement` | Facility | A batch on the shelf and every change to it | batch: drug\_id, batch\_number, expiry\_date, quantity\_on\_hand; movement: batch\_id, type, quantity, reference\_id, reason |
| `price_list_item` | Facility | The facility's cash price for a service | service\_code, price\_kobo, effective\_from, effective\_to |
| `charge` | Facility | One billable line | visit\_id, service\_code, quantity, amount\_kobo, patient\_kobo, payer\_kobo, tariff\_version\_id, status |
| `payment` | Facility | Money taken and the paper given | amount\_kobo, method, reference, receipt\_number (unique per facility; there is no separate receipt table), cash\_up\_id; created\_by is the cashier |
| `payer` / `payer_plan` | Platform | NHIA, an HMO, a state scheme, a programme, and its plans | payer: type, name; plan: payer\_id, name |
| `coverage` | Facility | A person's membership of a plan | person\_id, plan\_id, member\_number, valid\_from, valid\_to, status, verified\_by |
| `tariff_version` | Facility | A payer's agreed price here | payer\_id, service\_code, price\_kobo, effective\_from, effective\_to |
| `benefit_rule` | Platform | What a plan covers | plan\_id, service\_code or service\_category, reimbursement\_type, covered, copay\_bps or copay\_fixed\_kobo, requires\_preauth, effective\_from, effective\_to |
| `claim` / `claim_line` | Facility | A claim and its lines | claim: visit\_id, coverage\_id, status, exported\_at, submitted\_at, submission\_channel, payer\_reference; line: charge\_id, tariff\_version\_id, claimed\_kobo, approved\_kobo, rejection\_code |
| `audit_event` | Facility | Who did what | seq, actor\_user\_id, action, resource\_type, resource\_id, person\_id, purpose, decision, policy\_version, break\_glass |
| `audit_seal` / `audit_seal_member` | Facility | A seal and the events it covers | seal: merkle\_root, prev\_seal\_hash, seal\_hash, event\_count, sealed\_at; member: seal\_id, event\_id; shipment table: seal\_id, shipped\_at, central\_ack |
| `outbox_entry` | Facility | A change waiting to go to the central platform | aggregate\_type, aggregate\_id, payload, idempotency\_key, status |
| `idempotency_record` | Facility | A remembered write | idempotency\_key, request\_hash, response\_status, response\_body, expires\_at |

The audit tables are the deliberate exception to the base columns: `audit_event`, `audit_seal`, `audit_seal_member` and `audit_seal_shipment` are insert-only, so they have `created_at` but never `updated_at`, `row_version` or `deleted_at`.

Feature specs also name smaller supporting tables, such as `clinic`, `hospital_number_sequence`, `policy`\_set, `risk_event`, `encounter_addendum`, `lab_result_view`, `result_acknowledgement`, `formulary_item`, `dispense`, `payment_line`, `refund`, `patient_credit`, `receipt_number_sequence`, `cash_up`, `utilisation_record`, `claim_export`, `claim_response` and `export_profile`, plus reference tables (`icd10_code`, `drug`, `lab_test`, `lab_analyte`, `frequency`, `consent_type`, `rejection_reason`). They follow the column rules of their record category (section 8); reference tables are platform-scoped and read-only to the application.

## 10. API conventions

Every endpoint follows these conventions, so a frontend engineer can call a new endpoint without reading its code.

| Topic | Convention |
| --- | --- |
| Base path | `/api/v1`. Breaking changes go to `/api/v2`; the old version stays until no facility uses it. |
| Format | JSON, field names in camelCase, IDs as UUID strings. |
| Authentication | Session cookie set at sign-in (`HttpOnly`, `Secure`, `SameSite=Strict`). No tokens in local storage. |
| Facility | Taken from the session's active facility. Never accepted in a URL, query or body. |
| Money | Integers in kobo, field names ending in `Kobo`, for example `amountKobo: 500000`. |
| Dates and times | ISO 8601. Instants in UTC (`2026-10-15T08:10:00Z`); calendar dates as `2026-10-15`. |
| Writes | `POST` and `PATCH` require an `Idempotency-Key` header (UUID). `PATCH` also requires `If-Match` with the row version. |
| Lists | Cursor pagination: `?limit=50&cursor=...`, default 50, maximum 100. Responses return `items` and `nextCursor`. |
| Errors | `application/problem+json` (RFC 9457) with `type`, `title`, `status`, `detail` and a stable `code`, such as `DUPLICATE_SUSPECTED`. |
| Not found and not allowed | A record in another facility, or one the user may not see, returns 404, so its existence is not revealed. An allowed record with a forbidden action returns 403. |
| Step-up | When the risk check needs re-authentication, the API returns 401 with code `STEP_UP_REQUIRED`; the client asks for the password or MFA code and retries with the same idempotency key. |

### Worked example: registering a patient

Request:

```http
POST /api/v1/registrations
Idempotency-Key: 0192f0a4-6c1e-7b3a-9d2f-5e8a1c4b7d90
Content-Type: application/json

{
  "firstName": "Bisi",
  "lastName": "Adeyemi",
  "sex": "female",
  "dateOfBirth": "1972-03-14",
  "dateOfBirthEstimated": false,
  "phone": "08031234567",
  "address": { "line": "12 Isolo Road", "lga": "Oshodi-Isolo", "state": "Lagos" },
  "nextOfKin": { "name": "Tunde Adeyemi", "phone": "08037654321", "relationship": "son" },
  "confirmNoDuplicate": false
}
```

If likely duplicates exist, the server does not register. It answers 409 with code `DUPLICATE_SUSPECTED` and the candidates:

```json
{
  "type": "https://ereach.ng/problems/duplicate-suspected",
  "title": "Possible existing patient",
  "status": 409,
  "code": "DUPLICATE_SUSPECTED",
  "candidates": [
    { "personId": "0191c3e2-...", "hospitalNumber": "GH/2026/000123", "fullName": "Bisi Adeyemi", "age": 54, "phoneLast4": "4567", "matchScore": 0.93 }
  ]
}
```

The records officer either opens the existing patient, or resends with `confirmNoDuplicate: true` and a `duplicateOverrideReason`, using a new idempotency key because it is a new decision. On success the server answers 201 with the registration, including the new `hospitalNumber` and `personId`.

## 11. Feature specifications

Every spec has the same parts: the sprint it belongs to, the goal, what the user does, the rules, the acceptance criteria (tick them in your pull request), the endpoints and the tables. Endpoint paths are relative to `/api/v1`.

### F1. Facility setup and tenancy

**Sprint 1.** **Goal:** a facility exists, has a first administrator, and every request afterwards runs inside exactly one facility.

**What the user does**

1. The platform operator creates a facility: name, type (`phc`, `clinic`, `secondary`, `tertiary`), state, LGA, hospital number prefix (for example `GH`).
2. The system creates the facility's first `facility_admin` account with a one-time password, shown once to the operator.
3. The administrator signs in, sets a new password and MFA, and configures clinics (for example GOPD, Medical Outpatient, Surgical Outpatient, Paediatrics, ANC) and facility settings.

**Rules**

- The facility for a request comes from the session's active membership, never from the request.
- Database tables are owned by a migration role. The application connects as a separate role that does not own the tables, and every facility-scoped table has `ENABLE ROW LEVEL SECURITY` and `FORCE ROW LEVEL SECURITY`, so even a mistake cannot bypass RLS.
- Each request transaction begins with `set_config('app.facility_id', …, true)` and `set_config('app.user_id', …, true)` (section 8, rule 1). If the facility setting is missing, RLS returns no rows.
- Platform reference tables (ICD-10, drug catalogue, lab test catalogue, payers, plans, benefit rules, consent types) have no `facility_id` and are read-only to the application role.
- Facility settings in the MVP, all with defaults: `gate.consultation_requires_payment` (false), `gate.lab_requires_payment` (true), `gate.pharmacy_requires_payment` (true), `lab.verification_required` (true), `session.idle_lock_minutes` (10), session.max\_hours (12), receipt.gapless (true).

**Acceptance criteria**

- [ ] Creating a facility also creates its first `facility_admin` with a one-time password that is shown once, cannot be shown again, and must be changed at first sign-in.
- [ ] The hospital number prefix is unique across the platform, 2 to 6 capital letters, and cannot be changed once a patient is registered.
- [ ] Clinic codes are unique within a facility; a clinic with open visits cannot be deactivated.
- [ ] Setting an unknown key, or a value of the wrong type (text for a true/false setting), is refused with a clear error.
- [ ] Changing a setting writes an audit event with the old and new value and takes effect on the next request.
- [ ] A user at facility A requesting a record ID that belongs to facility B gets 404.
- [ ] An integration test connects as the application role without setting `app.facility_id` and gets zero rows from every facility-scoped table.
- [ ] Two consecutive requests for different facilities on the same pooled connection each see only their own facility's rows.
- [ ] A test lists every table in the schema and fails if a table with `facility_id` lacks row-level security, so a new table cannot forget it.
- [ ] The application role cannot insert into, update or delete from reference tables.
- [ ] A `platform_operator` cannot read any facility's patient data.
- [ ] A deactivated facility refuses all sign-ins, and its data stays intact.
- [ ] Creating a facility writes an audit event.

**Endpoints:** `POST /platform/facilities` (platform\_operator), `GET /facility` (current facility), `PUT /facility/settings/{key}` (facility\_admin), `POST /facility/clinics`, `GET /facility/clinics`.

**Tables:** `facility`, `facility_setting`, `clinic`.

### F2. Users, memberships and sign-in

**Sprint 1.** **Goal:** staff sign in safely on shared hospital PCs, and one account can work in several facilities. Only staff have accounts; patients never sign in. A clinician is a licensed health worker (doctor, nurse or midwife, pharmacist, lab scientist); only clinicians get a practitioner profile, and a locum is a clinician on a short-term membership.

**What the user does**

1. The facility administrator creates a user (full name, username, phone), or finds an existing platform user by username, and adds a membership: department, start date, optional end date, and one or more roles.
2. For clinicians, the administrator records the practitioner profile: cadre, licensing body (MDCN, NMCN, PCN, MLSCN), licence number, specialty.
3. The new user signs in with a one-time password and must set their own password and a 4 to 6 digit unlock PIN.
4. A user with memberships at several facilities picks one after sign-in and can switch later without signing out.
5. After 10 idle minutes the screen locks. The same user unlocks with the PIN; anyone else must sign in fully, which ends the first user's session.

**Rules**

- Usernames are unique across the platform and stored lower-case. In the MVP the facility server creates and checks accounts itself; before a second facility joins, the central platform becomes the owner of accounts and practitioner profiles, and facilities work from a cached copy (decision B2-2 in section 17).
- Passwords: at least 10 characters, hashed with Argon2id, never logged. Five failed attempts within 15 minutes lock the account for 15 minutes. The unlock PIN is hashed with Argon2id too.
- MFA by authenticator app (TOTP) is required for `facility_admin`, `data_protection_lead` and `platform_operator`.
- A full sign-in is required once per shift: after 12 hours by default, configurable per facility. Within the shift, staff unlock with their PIN. A full sign-in should take under 30 seconds.
- A membership grants access only while its status is `active` and today is within `valid_from` to `valid_to`.
- Removing a role sets `deleted_at` on the role assignment; it takes effect on the user's next request. Every write carries the facility the screen was loaded under (header X-Facility-Context); if the session has since switched facility in another tab, the server refuses the write with 409 FACILITY\_CHANGED. Switching facility clears the frontend's cached data, and the active facility is always shown in the top bar.
- Signing in, failing to sign in, switching facility, locking and unlocking all write audit events.

**Acceptance criteria**

- [ ] A user with memberships at two facilities sees only facility A's data after choosing A, and only B's after switching.
- [ ] An expired or suspended membership cannot be chosen at sign-in.
- [ ] After five wrong passwords within 15 minutes, the next attempt is refused even with the correct password, and sign-in works again 15 minutes later.
- [ ] A facility admin cannot complete sign-in without a valid TOTP code.
- [ ] A locked session accepts the owner's PIN; a different user's sign-in on that browser invalidates the old session.
- [ ] Removing a role denies the next request that needed it.
- [ ] No password, PIN or TOTP secret appears in logs or audit events (test scans log output).

**Endpoints:** `POST /auth/sign-in`, `POST /auth/mfa`, `POST /auth/select-facility`, `POST /auth/unlock`, `POST /auth/sign-out`, `POST /users`, `GET /users?query=`, `POST /users/{id}/memberships`, `POST /memberships/{id}/roles`, `DELETE /memberships/{id}/roles/{roleCode}`, `PUT /users/{id}/practitioner-profile`.

**Tables:** `user_account`, `practitioner_profile`, `facility_membership`, `role_assignment`, and Spring Session's spring\_session and spring\_session\_attributes (created by migration V1\_1 from the library's own schema file).

### F3. Access control

**Sprints 1 to 6, one layer per sprint.** **Goal:** every read and action is decided by written policies that can be tested and audited, and nobody sees a patient's record without a reason the system recognises.

**How a decision is made**

1. The endpoint declares an action (for example `encounter.read`) and a purpose (for example `treatment`) with an annotation. Users never choose the purpose, except when using break-glass.
2. The policy engine builds a context with six parts, and only these six: **subject** (user, roles in the active facility, cadre), **resource** (type, facility, sensitivity, owner fields such as `person_id`), **action**, **purpose**, **environment** (time, client IP, session age, MFA done) and **relationship** (the user's active care relationships to the person).
3. It evaluates every policy that matches the action. No matching policy means deny. Any deny beats any allow.
4. The result is `allow`, `deny` or `step_up`, plus obligations such as "mark as break-glass".
5. The decision, the matching policy IDs and the loaded `policy_version` go into the audit event.

**Policies are YAML files in `backend/facility-server/src/main/resources/policies/`.** They are reviewed like code, tested like code, and shipped inside the signed release; nobody edits them in production. The server always reads them from the release, never from the database. On startup it records the policy set, with its SHA-256 checksum as the version, in the policy\_set table, and every audit event stores that version (Batch 2, ADR-008). At startup Jackson reads each file into a typed Policy record, the loader checks it against the allowed vocabulary, and the compiler turns it into both the in-memory evaluator and the SQL condition. Example:

```yaml
id: encounter-read-by-carer
version: 3
effect: allow
actions: [encounter.read]
resource: encounter
when:
  subject.roles.any: [doctor, nurse]
  relationship.types.any: [treating_clinician, care_team, visit_assignee]
  purpose.in: [treatment]
```

**The same policy serves lists.** The policy compiler turns the `when` block into a SQL condition, so `GET /encounters` adds, for example, `EXISTS (SELECT 1 FROM care_relationship cr WHERE cr.person_id = e.person_id AND cr.user_id = :userId AND cr.status = 'active')` before `LIMIT`. A condition that cannot be compiled to SQL is not allowed in the vocabulary. The split is simple: conditions about the request (time, MFA done, client IP, risk score, step-up) are checked once in memory before the query, because they are the same for every row; conditions about a row (facility, care relationship, consent, sensitivity) must compile to SQL. Deny policies become AND NOT (...), and break-glass is one more relationship type in the join, so list and single-record answers agree by construction.

**Layers, in delivery order**

| Sprint | Layer | What it adds | Example |
| --- | --- | --- | --- |
| 1 | RBAC and facility scope | Permissions from roles in the active facility | Only `cashier` can record a payment |
| 2 | ReBAC | Clinical records need a care relationship | Dr Musa reads Mrs Adeyemi's encounter because he is her treating clinician on today's visit |
| 3 | ABAC | Decisions use attributes such as sensitivity and consent | A record marked `sensitive` needs the `doctor` role even with a relationship |
| 4 | PBAC | Purpose limits what each workflow may touch | Billing screens (`payment` purpose) see charges and the patient header, never consultation notes |
| 5 | RuBAC | Guardrails that apply whatever the role | Any export of more than 100 rows of patient-level data needs `data_protection_lead` approval |
| 6 | RAdAC | A risk score can demand step-up or deny | Opening more than 30 different patients in 10 minutes requires re-entering the password (assumed thresholds) |

**Care relationships (sprint 2)** are created automatically, never by hand:

- Assigning a visit to a doctor's queue, or a doctor starting the encounter, creates `treating_clinician` for that doctor. It gives full access while the visit is open and read-only access for 24 hours after it closes (assumed), and ends at once on handover to another doctor. The other kinds of access in the table below are separate and expire on their own terms.
- The triage nurse gets `visit_assignee` while the visit is open; it ends when the visit closes. Responsibility for results is separate from treatment access; see the table below.
- Lab scientists and pharmacists do not get care relationships. Their policies give them their worklists plus a minimal patient header (name, hospital number, age, sex, allergies for pharmacists), not the clinical record.

**Five kinds of clinical access, each with its own life:**

| Kind | Who | What it allows | Ends when |
| --- | --- | --- | --- |
| Treatment | The treating clinician | Full clinical access to the patient | 24 hours after the visit closes, or at once on handover |
| Authorship | The author of an encounter | Reading that encounter and adding addenda to it | Never; addenda are always allowed to their author |
| Result responsibility | The ordering doctor, or whoever received the handover | Reading and acknowledging that order's results, with the encounter that ordered them | When the result is acknowledged |
| Covering | Doctors on a clinic's cover rota, set by the facility admin | The same as treatment, for that clinic's patients | When the rota period ends |
| Exceptional (break-glass) | A doctor or nurse with a stated reason | Lifts the relationship requirement only, within the user's own role | 60 minutes, then reviewed |

**Break-glass (sprint 2)**

- A `doctor` or `nurse` without a relationship may open a record by choosing a reason (`emergency`, `patient_unable_to_consent`, `other` with text).
- It lifts the care-relationship requirement for that person at this facility for 60 minutes (assumed). It never adds a permission the role lacks: a nurse under break-glass can read the record and record nursing observations, never diagnose, order or prescribe. The grant records the reason, why access is needed now, its scope, and which restriction it overrode.
- Every action under it is audited with `break_glass = true`, and the grant appears in the data protection lead's review queue, to be reviewed within 7 days.

**Acceptance criteria**

- [ ] An action with no matching policy is denied.
- [ ] When one policy allows and another denies, the result is deny.
- [ ] For every list endpoint, a property test confirms that every returned ID can also be read on its own, and that no readable ID is missing from the list.
- [ ] Each policy file has at least one allow test and one deny test, run in CI.
- [ ] A doctor without a care relationship gets 404 on the encounter; after break-glass with a reason the read succeeds and the audit event has `break_glass = true`.
- [ ] Policy evaluation adds under 10 ms at p95 to a single-record request.
- [ ] Changing a policy file without changing its `version` fails the build.

**Endpoints:** `POST /access/break-glass`, `GET /access/break-glass?status=unreviewed` (data\_protection\_lead), `POST /access/break-glass/{id}/review`.

**Tables:** `policy_set` (the audit record of each policy set and its checksum), `care_relationship`, `cover_rota`, `break_glass_grant`, `risk_event`.

### F4. Audit trail

**Sprint 1 for events, Sprint 2 for sealing.** **Goal:** anyone checking later can see who did what to which record, when, for what purpose and whether it was allowed, and can prove the record of it was not altered.

**What gets recorded.** One `audit_event` per access decision and per data change, written in the same transaction as the change: `seq` (an increasing number), `occurred_at` (server time), `actor_user_id`, `actor_roles`, `session_id`, `action`, `resource_type`, `resource_id`, `person_id` (when the record is about a patient), `purpose`, `decision`, `policy_ids`, `policy_version`, `break_glass`, `client_ip`. It never holds clinical text, names or patient values; only IDs and metadata, plus values for fields on the audit allowlist (B2-11), such as facility settings.

**How sealing works, step by step**

1. Events are written freely; nothing waits on a single "last hash" row.
2. Every 60 seconds, or when 500 unsealed events have built up, a sealer job picks every event that is not yet in a seal (a join against `audit_seal_member`), whatever its `seq`. This catches events from transactions that committed late.
3. It orders those events by `seq`, hashes each one (SHA-256 over a fixed canonical JSON form), and builds a Merkle root from the hashes.
4. It writes one `audit_seal` row with `seal_hash = SHA-256(prev_seal_hash + merkle_root + event_count + sealed_at)`, plus one `audit_seal_member` row per event. All in one transaction.
5. When the internet is up, the newest seal hash is sent to the central platform and the shipment recorded in `audit_seal_shipment`.
6. A verification job runs nightly and on demand. It recomputes every hash and seal and compares the newest seal with the copy held by the central platform.

**Rules**

- Change events join the business transaction, so they commit or roll back with the change. Denials are written in their own short transaction, so a failed request still leaves a record. The application role has `INSERT` and `SELECT` on audit tables and nothing else. No code path updates or deletes an audit row.
- An event older than 5 minutes that is not in any seal is a verification failure.
- Audit events are kept for at least 7 years (assumed, pending legal advice).
- Reading the audit trail is itself audited.

**Acceptance criteria**

- [ ] Every endpoint in the API test suite produces at least one audit event (a test checks this across all endpoints).
- [ ] `UPDATE audit_event ...` and `DELETE FROM audit_event ...` as the application role fail with a permission error.
- [ ] Changing one byte of one sealed event in the database (as the migration role, in a test) makes verification report that seal.
- [ ] Deleting a sealed event is detected, and an event sealed more than 5 minutes after its occurred\_at time is flagged as a possible backdated insert.
- [ ] Under a load test of 200 writes per second, no request waits on the sealer.
- [ ] A rolled-back data change leaves no change event behind, a committed one always has its event, and a denied request always leaves its denial event.

**Endpoints:** `GET /audit/events?personId=&userId=&from=&to=` (data\_protection\_lead), `POST /audit/verify` (data\_protection\_lead), `GET /audit/seals/latest`.

**Tables:** `audit_event`, `audit_seal`, `audit_seal_member`, `audit_seal_shipment`.

### F5. Consent

**Sprint 3.** **Goal:** record what each patient agreed to, so that sharing with a payer or contacting the patient only happens on a recorded basis, and a withdrawal works immediately.

**What the user does**

1. At registration, and whenever the patient's position changes, the records officer reads a short script and records each answer: given or refused.
2. When a visit is paid by insurance, the insurance officer confirms the patient agrees to share the visit's claim details with that payer.
3. If a patient withdraws consent, the records officer records the withdrawal with the date.

**Consent types in the MVP** (a platform reference list, so new types need no code change):

| Code | Meaning | Grantee | Used by |
| --- | --- | --- | --- |
| `payer_claim_sharing` | Send this visit's diagnoses and services to the named payer | The facility, for that payer | Claim submission (F13) |
| `sms_contact` | Contact the patient by SMS or WhatsApp | The facility | Phase 2 reminders; recorded now |
| `cross_facility_sharing` | Let other facilities read this record | Named facility or all | Phase 3; recorded now |

Treatment at the facility does not need a consent record in the MVP; the patient's attendance and the National Health Act's treatment provisions cover it (assumed, pending legal review).

**Rules**

- A consent record has `person_id`, `consent_type`, `grantee_facility_id`, `purpose`, `scope` (for `payer_claim_sharing`: the payer ID), `version` of the script read, `status` (`given`, `refused`, `withdrawn`), `decided_at` and `recorded_by`. Rows are never changed: a withdrawal is a new row with status `withdrawn`, and the newest row for a type, grantee and scope is the current decision.
- Records are never edited. A change is a new record; the newest record for a type, grantee and scope is the current one.
- Policies read consent at decision time, so a withdrawal applies to the very next request.

**Acceptance criteria**

- [ ] A claim cannot be marked submitted unless `payer_claim_sharing` for that payer is currently `given`.
- [ ] After a withdrawal, the next claim submission for that payer is refused with code `CONSENT_REQUIRED`.
- [ ] The consent history for a person shows every record, newest first, with who recorded it.

**Endpoints:** `GET /persons/{personId}/consents`, `POST /persons/{personId}/consents`.

**Tables:** `consent_record`, `consent_type` (reference).

### F6. Patient registration and search

**Sprint 1.** **Goal:** find any registered patient in seconds, and register a new one without creating a duplicate.

**What the user does**

1. The records officer types into one search box: a phone number, a hospital number, or a name (optionally with year of birth).
2. Results show hospital number, full name, sex, age and the last 4 digits of the phone, newest visit first.
3. If the patient is not found, the officer opens the registration form and fills it in. On save, the system checks for likely duplicates (see below) and, if any exist, shows them side by side with what was typed.
4. The officer either opens the existing patient, or confirms "this is a different person" with a reason.
5. The system shows the new hospital number, ready to write on the patient's card.

**Registration fields**

| Field | Required | Rules |
| --- | --- | --- |
| First name, last name | Yes | Trimmed; stored as typed, searched in a normalised form (lower-case, accents removed) |
| Middle name | No |  |
| Sex | Yes | `female`, `male`, `unknown` (for unconscious or unidentified patients) |
| Date of birth | Yes | Either an exact date, or an age in years from which a date of 1 July of that year is estimated with `dobEstimated = true` |
| Phone | No, but strongly prompted | Normalised to international form: `08031234567` becomes `+2348031234567`; Nigerian numbers are normalised to +234, foreign numbers are accepted in international form, and several patients may share one household number |
| Address line, LGA, state | No; prompted, but not required for foreign or unidentified patients | State and LGA from a reference list |
| Next of kin name, phone, relationship | No |  |
| NIN | No | Exactly 11 digits. Stored twice: a keyed hash for matching, and encrypted for display to authorised users |
| Legacy hospital number | No | For patients with an old paper folder; searchable |

**Duplicate check.** A candidate is any registration at this facility where the normalised phone matches exactly, or the NIN hash matches, or all of these hold: name similarity of at least 0.5 (PostgreSQL trigram similarity on the normalised full name), same sex (or either `unknown`), and birth years within 2 of each other. Show the top 5 by score. A phone or NIN match only makes someone a candidate; it never proves identity. An unidentified patient (for example unconscious) is registered with a temporary name such as "Unknown male 16/10/2026 #3", sex as observed or unknown, and an estimated age; real details replace them later through a normal, audited edit. Identifier types include phone, NIN, insurance member number, legacy hospital number, and matriculation and staff numbers for institutional facilities. The score formula lives in one class and is unit-tested.

**Hospital number.** Format `{PREFIX}/{YYYY}/{NNNNNN}`, for example `GH/2026/000123`. The sequence is per facility per year, generated by the facility server inside the registration transaction, and never reused, even if the registration is later soft-deleted.

**Rules**

- Registering creates a `person` row and a `patient_registration` row in one transaction. In the MVP every person is new to the platform, because cross-facility matching arrives in Phase 2.
- Reads of patient details always go through `patient_registration` for the active facility. Code never reads demographics from `person`, which only anchors the identity.
- Demographic edits use `If-Match`; every edit is audited with the names of the fields changed, never their values or hashes of them, because short values like a birth date are easy to guess from a hash.

**Acceptance criteria**

- [ ] Searching `0803 123 4567`, `08031234567` or `+2348031234567` finds the same patient.
- [ ] Registering a patient whose phone matches an existing registration returns 409 `DUPLICATE_SUSPECTED` with that candidate.
- [ ] Resending with `confirmNoDuplicate: true` and a reason registers the patient and records the override in the audit trail.
- [ ] Two officers registering at the same moment never get the same hospital number (concurrency test with 50 parallel requests).
- [ ] With 1.5 million registrations loaded, search returns in under 300 ms at p95 on the reference server.
- [ ] Only records officers, nurses, doctors, cashiers and insurance officers can search patients; a lab scientist gets 403.

**Endpoints:** `GET /patients/search?q=`, `POST /registrations`, `GET /registrations/{id}`, `PATCH /registrations/{id}`.

**Tables:** `person`, `patient_registration`, `person_identifier`, `hospital_number_sequence`.

### F7. Visits, queue and triage

**Sprint 2.** **Goal:** every patient in the building has an open visit, sits in exactly one queue, and the sickest are seen first.

**What the user does**

1. The records officer opens a visit for a registered patient: clinic, visit type (`new`, `follow_up`, `emergency`), and payment mode (`cash` or `insurance` with one of the patient's active coverages, see F13). Charges for the card fee (first visit only) and the clinic consultation fee are created automatically (F12).
2. The visit enters the triage queue for that clinic. Emergency visits jump to the top of every queue.
3. The nurse opens the patient from the triage queue, records vitals, chooses a priority (`emergency`, `urgent`, `routine`), and sends the visit to the clinic's doctor queue or to a named doctor.
4. Doctors see their queue ordered by priority, then by arrival time.
5. At the end, the doctor closes the visit with a disposition (F8). The records officer can mark a visit `left_without_being_seen` or `cancelled` with a reason.

**Vitals and accepted ranges.** Values outside the range are refused as likely typing errors; values inside but abnormal are accepted and highlighted.

| Vital | Unit | Accepted range | Highlighted when |
| --- | --- | --- | --- |
| Temperature | °C | 30.0 to 45.0 | 38.0 or above, or below 35.0 |
| Pulse | beats per minute | 20 to 250 | above 100 or below 50 |
| Respiratory rate | breaths per minute | 4 to 80 | above 24 or below 10 |
| Blood pressure | mmHg, systolic and diastolic | 50 to 300, 20 to 200; systolic above diastolic | systolic 140 or above, or diastolic 90 or above; systolic below 90 |
| Oxygen saturation | % | 50 to 100 | below 94 |
| Weight | kg | 0.3 to 400 | never |
| Height | cm | 20 to 250 | never |

BMI is calculated (weight in kg divided by height in metres squared) and shown, not stored. Highlighting applies only to patients aged 18 and over; for younger patients the screen shows "No automatic interpretation for children" and highlights nothing. Only clearly impossible values are refused; a value that is unusual but possible asks the nurse to confirm it, and the confirmation is recorded. A clinician must sign off these thresholds before real patients (validate before patients).

**Visit status.** `open` → `triaged` → `with_doctor` → `awaiting_services` (the doctor is waiting for same-day results before finishing) → `closed`. A visit with nothing pending goes from with\_doctor straight to closed. Also `left_without_being_seen` and `cancelled`, reachable from any state before `closed`. Visits still open at midnight appear on an "unclosed visits" list the next morning; they are not closed automatically.

**Rules**

- A person can have at most one clinically open visit per facility at a time. Pending service tasks and unpaid charges from an earlier visit never block a new one.
- Sending a visit to a doctor creates the doctor's `treating_clinician` care relationship (F3); triage creates the nurse's `visit_assignee` relationship.
- Each vital is one `observation` row with `recorded_at` set by the server.

**Acceptance criteria**

- [ ] Opening a second visit for a person with an open visit returns 409 `VISIT_ALREADY_OPEN`.
- [ ] A temperature of 375 is refused; 38.5 is accepted and highlighted.
- [ ] In a queue with a routine patient who arrived at 8:00 and an urgent one at 8:30, the urgent patient is first.
- [ ] An emergency visit can be opened and triaged without any payment, whatever the facility's gate settings.
- [ ] A status change not shown above (for example `closed` to `open`) is refused.

**Endpoints:** `POST /visits`, `GET /visits/{id}`, `GET /queues/triage?clinicId=`, `GET /queues/doctor?clinicId=&mine=true`, `POST /visits/{id}/triage`, `POST /visits/{id}/assign`, `POST /visits/{id}/status`.

**Tables:** `visit`, `observation`, `clinic`.

### F8. Consultation

**Sprint 2.** **Goal:** the doctor sees what matters before typing anything, and documents a routine visit in under 90 seconds on one screen.

**What the user does**

1. The doctor picks the next patient from their queue. The encounter opens and the visit becomes `with_doctor`.
2. The top of the screen shows, without any click: name, age, sex, hospital number, payment mode and payer, allergies (red if any), active medications, today's vitals with highlights, and lab results from the last 90 days with new or abnormal ones in bold.
3. Below, the doctor fills in: presenting complaint, history, examination, assessment, and plan. All are free text.
4. The doctor adds diagnoses by typing a few letters; the search shows ICD-10 codes with the facility's most-used ones first. Each diagnosis is `provisional` or `confirmed`.
5. From the same screen the doctor can record a new allergy, order lab tests (F9) and prescribe (F10).
6. The doctor closes with a disposition: `discharged`, `follow_up` (with a date), `admission_requested`, `referred_out` (free-text note only in the MVP) or `left_against_advice`. Then they sign.
7. After signing, the encounter is read-only. Corrections are addenda: dated, signed text attached underneath.

**Rules**

- Text sections save automatically when the doctor leaves a field and after 3 seconds without typing, so a power cut loses at most a few seconds of typing. Saved drafts are visible only to the author until signed.
- An encounter needs at least one diagnosis to be signed.
- Signing sets `signed_at` and `signed_by`. Any later write to the encounter's own fields returns 409 `ENCOUNTER_SIGNED`.
- Closing the visit requires a signed encounter. Clinical closure, pending services and money are tracked separately: the doctor closes the visit clinically; lab or pharmacy items still pending stay open as their own tasks; unpaid charges stay on the patient's account. If the doctor is waiting for same-day results, the visit moves to `awaiting_services` instead of `closed`, and closes itself when they finish.
- Allergies belong to the person at this facility, not to the encounter: substance, reaction, severity (`mild`, `moderate`, `severe`), recorded\_by.
- ICD-10 codes come from the platform reference table, loaded from the WHO ICD-10 release (assumed: the 2019 edition).

**Acceptance criteria**

- [ ] Opening an encounter shows allergies, active medications, today's vitals and recent results with no extra request from the user (the endpoint returns them together).
- [ ] A draft typed and then interrupted by closing the browser is still there when the doctor returns.
- [ ] Signing without a diagnosis is refused with `DIAGNOSIS_REQUIRED`.
- [ ] After signing, `PATCH /encounters/{id}` returns 409 and `POST /encounters/{id}/addenda` succeeds.
- [ ] Typing `mala` in the diagnosis search shows malaria codes within 300 ms.
- [ ] A pharmacist or cashier cannot read the encounter's notes (404), even for a patient in their queue.

**Endpoints:** `POST /visits/{visitId}/encounter`, `GET /encounters/{id}` (includes the header summary), `PATCH /encounters/{id}`, `POST /encounters/{id}/diagnoses`, `DELETE /encounters/{id}/diagnoses/{diagnosisId}` (before signing only), `POST /encounters/{id}/sign`, `POST /encounters/{id}/addenda`, `POST /persons/{personId}/allergies`, `GET /reference/icd10?q=`, `POST /visits/{id}/close`.

**Tables:** `encounter`, `encounter_addendum`, `condition`, `allergy`, `icd10_code` (reference).

### F9. Lab orders and results

**Sprint 3.** **Goal:** a test ordered by the doctor reaches the lab, is paid or covered before the specimen is taken, and its result reaches the doctor with abnormal values flagged.

**What the user does**

1. In the encounter, the doctor picks one or more tests from the lab catalogue (for example malaria parasite, full blood count, fasting blood sugar), sets priority (`routine`, `urgent`) and adds an optional clinical note. A charge per test is created at the facility's lab price (F12).
2. The lab scientist sees the worklist: patient header, tests, priority, and whether each is cleared for collection (paid or covered).
3. When cleared, the scientist records specimen collection (specimen type, collected\_at).
4. The scientist enters results, one value per analyte. A test like a full blood count has several analytes; a malaria test has one.
5. If the facility requires verification (the default), a second scientist, or the same one if the facility allows, verifies the results. Only users whose practitioner cadre is medical laboratory scientist may verify; technicians may enter results but not verify. Only verified results show to the doctor.
6. The doctor sees new results in bold on the encounter header and on a "new results" list until they acknowledge them (opening is not acknowledging).

**Order status.** `ordered` → `c``ollected` → `resulted` → `verified`. Also `cancelled` (with reason, before collection only). Whether an order is cleared for collection (paid, covered, or emergency visit) is not a stored status: billing.isClearedForService computes it each time, so it can never disagree with the money.

**Rules**

- The lab catalogue is platform reference data: test code, name, analytes, unit, reference range (low and high, optionally by sex and age band). The price is the facility's (`price_list_item`).
- A numeric result outside its reference range gets `abnormal_flag` `low` or `high` automatically. The scientist can also set `abnormal` on a text result, for example "Plasmodium falciparum seen".
- A verified result is never edited. A correction is a new result row that supersedes the old one, and the doctor sees both, the old one struck through.
- Results are clinical data: the doctor reads them through a care relationship; lab staff see only results for their worklist.

**Acceptance criteria**

- [ ] With `gate.lab_requires_payment = true`, recording collection on an unpaid, uninsured, non-emergency order returns 409 `PAYMENT_REQUIRED`.
- [ ] A haemoglobin of 7.2 g/dL against a female range of 12.0 to 16.0 is flagged `low`.
- [ ] An unverified result does not appear to the doctor.
- [ ] Correcting a verified result leaves the original visible as superseded, and both are in the audit trail.
- [ ] The doctor's "new results" list marks a result seen when opened but keeps it listed until the responsible doctor acknowledges it; an abnormal result not acknowledged within 24 hours (assumed) appears on the medical director's escalation list.

**Endpoints:** `POST /encounters/{id}/lab-orders`, `GET /lab/worklist?status=`, `POST /lab-orders/{id}/collect`, `POST /lab-orders/{id}/results`, `POST /lab-orders/{id}/verify`, `POST /lab-results/{id}/supersede`, `POST /lab-orders/{id}/cancel`, `GET /doctors/me/new-results`, `GET /reference/lab-tests?q=`.

**Tables:** `lab_order`, `lab_result`, `lab_result_verification`, `lab_result_view` (who opened which result, and when), `result_acknowledgement`; `lab_test`, `lab_analyte` and `lab_reference_range` (reference).

### F10. Prescriptions

**Sprint 3.** **Goal:** a doctor prescribes in under 20 seconds, is warned before an allergy clash or a duplicate drug, and a signed prescription can never change silently.

**What the user does**

1. In the encounter, the doctor types part of a drug name. The search covers the facility formulary, matching both generic names and brand names, and shows strength and form (for example "Amlodipine 5 mg tablet").
2. The doctor fills dose, dose unit, route, frequency and duration. Quantity is suggested and can be edited.
3. The system checks each item against the patient's allergies and active prescriptions and shows any warning beside the item.
4. The doctor signs the prescription. It goes to the pharmacy queue.
5. To change a signed prescription, the doctor amends it: the system copies it into a new version for editing, and on signing the old version becomes `superseded`. Undispensed items can instead be cancelled with a reason.

**Frequencies** (a reference list, extendable without code):

| Code | Meaning | Times per day for quantity |
| --- | --- | --- |
| `OD` | Once daily | 1 |
| `BD` | Twice daily | 2 |
| `TDS` | Three times daily | 3 |
| `QDS` | Four times daily | 4 |
| `NOCTE` | At night | 1 |
| `STAT` | Once, immediately | none: quantity is the dose |
| `PRN` | When needed | none: quantity must be entered |

**Quantity suggestion** applies to countable forms (tablets, capsules) only: dose count × times per day × duration in days. Example: 1 tablet BD for 5 days = 1 × 2 × 5 = 10 tablets. For liquids and other forms the doctor enters the quantity.

**Warnings**

- **Allergy clash:** the drug's generic name or its drug class (from the drug catalogue) matches a recorded allergy substance. The doctor must type an override reason to keep the item; the reason is stored on the item and audited.
- **Duplicate therapy:** the patient has an active prescription item with the same generic name whose duration has not ended. The doctor sees it and can continue without a reason.

**Rules**

- The formulary is the facility's list of drugs it stocks or allows, each linked to a platform drug catalogue entry (generic name, class, strength, form).
- Status of a prescription: `draft` → `signed` → `superseded` or `completed` (all items dispensed, closed or cancelled). Items: `pending`, `quoted`, `partly_dispensed`, `dispensed`, `closed` (the pharmacist ended it because the patient will not return for the rest, F11), `cancelled`. A `closed` or `cancelled` item carries a `status_reason`.
- A signed prescription and its items are never updated except for status fields.

**Acceptance criteria**

- [ ] Prescribing amoxicillin to a patient with a recorded penicillin allergy shows the clash (amoxicillin's class is penicillin) and cannot be signed without an override reason.
- [ ] 1 tablet TDS for 7 days suggests 21.
- [ ] Amending a signed prescription creates version 2 pointing to version 1; version 1 becomes `superseded`; items already dispensed under version 1 stay linked to version 1.
- [ ] `PATCH /prescriptions/{id}` on a signed prescription returns 409 `PRESCRIPTION_SIGNED`.
- [ ] A full prescription with three items can be built and signed through the API in a test without any extra lookup calls besides the drug search.

**Endpoints:** `GET /formulary/search?q=`, `POST /encounters/{id}/prescriptions`, `PATCH /prescriptions/{id}` (draft only), `POST /prescriptions/{id}/sign`, `POST /prescriptions/{id}/amend`, `POST /prescription-items/{id}/cancel`.

**Tables:** `prescription`, `prescription_item`, `formulary_item` (facility), `drug` (platform reference), `frequency` (reference).

### F11. Pharmacy and stock

**Sprint 4.** **Goal:** the pharmacy dispenses only what was prescribed and paid for, from batches that are not expired, and the stock figure on screen is always true.

**What the user does**

1. **Receive stock.** The pharmacist records a delivery line: formulary item, batch number, expiry date, quantity, unit cost in kobo, supplier. Each line becomes a stock batch and a `receipt` movement.
2. **Quote.** From the pharmacy queue the pharmacist opens a signed prescription, sees stock per item, and marks which items and quantities the hospital will supply (the patient may buy some elsewhere). Marked items become `quoted` and charges are created at the facility's drug price (F12).
3. **Dispense.** Once an item is cleared (paid, covered, or emergency visit), the pharmacist dispenses. The system proposes the batch with the earliest expiry that is not expired and has enough stock; the pharmacist can pick another valid batch. Partial dispensing is allowed when stock is short. If the patient will not return for the rest, the pharmacist closes the item, and what was paid for the undispensed units becomes a refund or patient credit (F12).
4. **Adjust.** For a count difference, damage, or expiry write-off, the pharmacist records an adjustment with a reason.
5. **Watch.** Two lists show items below their reorder level and batches expiring within 90 days.

**Rules**

- Every change to stock is a row in `stock_movement`: `receipt` (+), `dispense` (−), `adjustment` (+ or −, reason required), `expiry_write_off` (−). Movements are never edited; a mistake is fixed with a new adjustment.
- `stock_batch.quantity_on_hand` is a cached balance. It is updated in the same transaction as the movement, while the batch row is locked with `SELECT ... FOR UPDATE`, and a check constraint keeps it at zero or above.
- A nightly job recomputes every batch balance from its movements and reports any difference.
- A batch whose expiry date has passed cannot be dispensed from.
- Quantities are whole numbers in the drug's dispensing unit (tablet, capsule, bottle, vial). Adjustments and expiry write-offs need a user whose cadre is pharmacist; pharmacy technicians can receive stock, quote and dispense.

**Acceptance criteria**

- [ ] Two pharmacists dispensing the last 10 tablets of a batch at the same moment: one succeeds, the other gets 409 `INSUFFICIENT_STOCK`, and the balance is 0, never negative.
- [ ] Dispensing an uncleared item with `gate.pharmacy_requires_payment = true` returns 409 `PAYMENT_REQUIRED`.
- [ ] The proposed batch is the earliest-expiring valid batch; an expired batch is never proposed or accepted.
- [ ] Partial dispensing of 6 out of 10 sets the item to `partly_dispensed` with 4 outstanding.
- [ ] After any sequence of receipts, dispenses and adjustments in a test, the cached balance equals the sum of movements.

**Endpoints:** `POST /stock/receipts`, `GET /pharmacy/queue`, `POST /prescriptions/{id}/quote`, `POST /prescription-items/{id}/dispense`, `POST /stock/adjustments`, `GET /stock/batches?formularyItemId=`, `GET /stock/low`, `GET /stock/expiring?days=90`.

**Tables:** `formulary_item` (with reorder\_level and price via `price_list_item`), `stock_batch`, `stock_movement`, `dispense`.

### F12. Billing and payments

**Sprint 4.** **Goal:** every service delivered becomes a charge, every naira taken has a receipt, and each cashier's day balances to the kobo.

**Where charges come from** (created automatically; nobody types a charge by hand in the MVP):

| Event | Service code | Quantity |
| --- | --- | --- |
| First visit of a new registration | `CARD` | 1 |
| Visit opened | `CONSULT-{clinicCode}` | 1 |
| Lab test ordered | the lab test code | 1 |
| Prescription item quoted by the pharmacist | the formulary item code | quoted quantity |

**How a charge is priced.** For a cash visit: `amount_kobo = facility price × quantity`, `patient_kobo = amount_kobo`, `payer_kobo = 0`. For an insurance visit, the payer layer prices it (F13) and splits it into `patient_kobo` and `payer_kobo`. Prices are those in force at the moment the charge is created; later price changes never touch existing charges.

**What the user does**

1. The cashier searches the patient and sees unpaid charges for the open visit, with the patient's share of each.
2. The cashier selects lines, takes the money by `cash`, `pos` or `bank_transfer` (with a reference for the last two), and the system prints a receipt numbered `R/{YYYY}/{NNNNNN}` per facility.
3. Whether a line is **cleared** is decided by one function in billing, `isClearedForService`, which lab, pharmacy and billing all call; no module decides it alone. A line is cleared when the visit is an emergency, or when all of these hold: any required pre-authorisation code is recorded, the coverage was active on the service date, and the patient share is paid, zero or waived.
4. A waiver needs the `medical_director` role and a reason; it sets the patient share to waived, not to zero, so reports show it.
5. A payment taken by mistake is voided by an `accounts_officer` the same day with a reason. A void is a new reversing record; the original payment stays.
6. At the end of the shift, the cashier runs the cash-up: totals by payment method, number of receipts and voids. Closing the cash-up locks those payments.

**Rules**

- Charge status (`unpaid`, `paid`, `covered`, `waived`, `void`) is derived, never stored: the `charge_status` view reads it from linked insert-only rows. A `charge_void` makes it `void` (service cancelled before payment); a `charge_waiver` makes it `waived`; a zero patient share makes it `covered`; payment lines from payments not voided that reach the patient share make it `paid`; otherwise it is `unpaid`. The charge row itself is never updated (Batch 2, ADR-012).
- MVP payments settle whole lines; part-payment of a line is not supported (assumed). Corrections never edit a payment. A **refund** (money returned) or a **patient credit** (kept against future charges) is a new record linked to the original charge, with a reason and `accounts_officer` approval. This covers a paid test cancelled before collection, paid units the pharmacy never dispensed, and a prescription amended after payment. A mistake found after a cash-up is closed is corrected by an adjustment in the next open cash-up; the closed one is never reopened.
- Payment amount must equal the sum of the selected lines' patient shares exactly.
- All arithmetic is integer kobo. Display formats kobo as naira with two decimals only in the frontend.

**Acceptance criteria**

- [ ] Opening a cash visit at a clinic with a ₦3,000 consultation fee creates a charge of `amount_kobo = 300000`.
- [ ] Paying two lines of 300000 and 250000 kobo with a payment of 550000 succeeds; 549999 is refused.
- [ ] The same payment request sent twice with the same idempotency key creates one payment and one receipt.
- [ ] With receipt.gapless on, receipt numbers have no gaps and no repeats under 50 parallel payments: the number is taken inside the payment transaction, so a failed payment uses none, a failed print reprints the same number, and a void keeps its number marked void. With it off, numbers are unique and traceable but may skip.
- [ ] A void after the cash-up is closed is refused.
- [ ] Cash-up totals equal the sum of that cashier's non-voided payments for the day.

**Endpoints:** `GET /visits/{id}/charges`, `POST /payments`, `GET /receipts/{id}` (printable), `POST /payments/{id}/void`, `POST /charges/{id}/waive`, `POST /cash-ups`, `GET /cash-ups/{id}`, `PUT /price-list/{serviceCode}` (facility\_admin, creates a new effective-dated version).

**Tables:** `service`, `price_list_item`, `charge`, `charge_preauth`, `charge_waiver`, `charge_void`, `payment` (which holds the receipt number), `payment_line`, `payment_void`, `receipt_number_sequence`, `refund`, `patient_credit`, `cash_up`, `cash_up_adjustment`, and the `charge_status` view.

### F13. Payer layer and claims

**Sprint 5.** **Goal:** for an insured patient, every charge is split correctly into what the patient pays and what the payer owes, and every visit becomes a complete claim that can be exported and tracked to payment.

**The pieces**

| Piece | Owned by | What it holds |
| --- | --- | --- |
| Payer | Platform | Type (`nhia`, `hmo`, `state_scheme`, `government_programme`, institution) and name, for example LASHMA (Ilera Eko), OGSHIA, an HMO |
| Plan | Platform | A payer's product, for example "Ilera Eko individual" |
| Benefit rule | Platform | For a plan and a service code or service category: covered or not, co-payment, maximum quantity per visit, whether pre-authorisation is needed, effective dates |
| Tariff version | Facility | The price this payer pays this facility for a service code, with effective dates |
| Coverage | Facility | A person's membership of a plan: member number, principal or dependant, valid dates, status, who verified it and how |
| Export profile | Facility | How claims for this payer are laid out in the export file |

**What the user does**

1. The insurance officer records the patient's coverage from their card and verifies it the way the payer allows today (portal, phone or card check), recording method and time.
2. At records, an insured visit is opened with that coverage. From then on every charge for the visit is priced by the steps below.
3. If a line needs pre-authorisation, the officer obtains a code from the payer and enters it on the line.
4. When the visit closes, a draft claim is created automatically. The officer reviews it, fixes anything the validator flags, and marks it ready.
5. The officer exports all ready claims for a payer as one file, uploads it on the payer's portal, and later records the payer's response per line and the payment received.

**How one charge is priced, in order**

1. Check the coverage is active on the service date. If not, price the line as cash and warn the officer.
2. Find the benefit rule: a rule for the exact service code beats a rule for its category. No rule means not covered (assumed).
3. Not covered: patient pays the facility cash price, payer pays 0.
4. Covered: find the tariff version in force on the service date. If none exists, flag the line `TARIFF_MISSING`; it cannot be claimed until a tariff is added or the line is converted to cash.
5. `amount_kobo = tariff price × quantity`. Units above the rule's maximum per visit are split into a separate line priced as not covered.
6. Co-payment: `copay_kobo = (amount_kobo × copay_bps + 5000) / 10000` using whole-number division, or the fixed amount `min(copay_fixed_kobo, amount_kobo)`.
7. `patient_kobo = copay_kobo`; `payer_kobo = amount_kobo − copay_kobo`; the charge stores the `tariff_version_id` it used.
8. If the rule needs pre-authorisation and no code is recorded, the line is not cleared (F12) until a code is entered, or the patient chooses to pay the cash price instead.

**Worked example.** Ilera Eko pays this facility ₦4,000 for a GOPD consultation, so the tariff is `400000` kobo, and the plan's co-payment is 10%, which is `1000` basis points. Co-payment = (400000 × 1000 + 5000) / 10000 = 400005000 / 10000 = 40000 kobo after whole-number division. The patient pays `40000` kobo (₦400) and the claim line is `360000` kobo (₦3,600).

**Claim status.** `draft` → `ready` → `exported` (file produced) → `submitted` (the officer uploaded it and recorded the date, channel, payer reference and their name) → `acknowledged` → `approved`, `partly_approved` or `rejected` → `paid`. A payer query moves a claim to `queried`; after the fix it goes back to `ready`. Exporting a file never marks a claim submitted, because a file on a desktop has not reached anyone.

**A claim is ready only when** it has a member number, coverage active on the service date, at least one ICD-10 diagnosis, the encounter's doctor with a licence number on their practitioner profile, a tariff version on every line, a pre-authorisation code on every line that needs one, and current `payer_claim_sharing` consent for that payer (F5).

**Rules**

- Every plan and benefit rule has a reimbursement type: fee\_for\_service, capitated, bundled or institution\_funded. Only fee\_for\_service and bundled lines become claim lines. A capitated or institution-funded service creates a utilisation record with the service and its tariff value, so the hospital can see what the capitation or institution is paying for, but nothing is claimed and no payer receivable appears. One claim per visit per coverage. Claim lines point to charges and copy `tariff_version_id` and `claimed_kobo`.
- Tariffs and benefit rules are never edited: a change is a new version with a new effective date. A database exclusion constraint stops two versions of the same tariff overlapping in time.
- Export profiles map claim fields to file columns. The MVP ships one generic CSV profile; payer-specific profiles are configured during the pilot from the payers' real templates (assumed).
- Each export records the file's SHA-256 hash, the claims in it, who exported it and when.
- Payer responses are entered per line: `approved_kobo` and, when reduced or rejected, a rejection code from a reference list plus free text.

**Acceptance criteria**

- [ ] The worked example above produces exactly `patient_kobo = 40000` and `payer_kobo = 360000`.
- [ ] A service with a code-level rule and a category-level rule uses the code-level rule.
- [ ] Creating a tariff version that overlaps an existing one for the same payer, facility and service is refused by the database.
- [ ] Changing a tariff after a claim was exported leaves that claim's lines unchanged.
- [ ] A claim missing the doctor's licence number cannot be marked ready, and the validator names the missing field.
- [ ] Exporting three ready claims for one payer produces one file, moves the claims to `exported` (not `submitted`), and records the file hash; recording the upload moves them to `submitted`.
- [ ] A visit with an expired coverage is priced as cash and the officer sees a warning.

**Endpoints:** `POST /persons/{personId}/coverages`, `POST /coverages/{id}/verify`, `GET /payers`, `GET /payers/{id}/plans`, `POST /tariffs` (new version), `GET /tariffs?payerId=&serviceCode=`, `POST /charges/{id}/preauth`, `POST /charges/{id}/convert-to-cash`, `GET /claims?status=&payerId=`, `GET /claims/{id}`, `POST /claims/{id}/validate`, `POST /claims/{id}/ready`, `POST /claim-exports`, `GET /claim-exports/{id}/file`, `POST /claims/{id}/responses`, `POST /claims/{id}/payments`.

**Tables:** `payer`, `payer_plan`, `benefit_rule`, `tariff_version`, `coverage`, `claim`, `claim_line`, `claim_export`, `claim_response`, `export_profile`, `rejection_reason` (reference).

### F14. Reports

**Sprint 6.** **Goal:** the medical director sees yesterday's activity, money and stock on one morning page, and every figure can be traced to the records behind it.

**Reports in the MVP**

| Report | For | What it shows |
| --- | --- | --- |
| Daily OPD register | Records, medical director | One row per visit: date, hospital number, name, age, sex, clinic, new or follow-up, diagnoses (ICD-10), disposition |
| Attendance summary | Medical director | Visits by clinic, new versus follow-up, sex and age band, for a date range |
| Top diagnoses | Medical director | The 20 most frequent ICD-10 codes in a date range |
| Revenue summary | Medical director, accounts officer | Payments by method, by service category, waivers and voids, for a date range |
| Claims summary | Insurance officer, medical director | Claims by payer and status, claimed versus approved versus paid kobo |
| Stock report | Pharmacist | Balance per item, items below reorder level, batches expiring within 90 days |
| Break-glass log | Data protection lead | Every break-glass grant, its reason, and whether it was reviewed |

**Rules**

- Default age bands: 0 to 4, 5 to 14, 15 to 24, 25 to 49, 50 to 64, 65 and above (assumed; configurable per facility).
- Reports are read-only queries on the facility server and go through the same policies as everything else; each report has its own action, for example `report.revenue.read`.
- Every report can be downloaded as CSV. A download over 100 rows of patient-level data needs `data_protection_lead` approval (the guardrail from F3).
- Totals in a report must equal the sum of its rows; money totals are summed in kobo and formatted at the end.

**Acceptance criteria**

- [ ] For a seeded test day with known data, every report returns the exact expected figures.
- [ ] The revenue summary shows cash collected, charges billed, amounts owed by payers, waivers and refunds as separate figures, using Africa/Lagos days; cash collected for a day equals the sum of that day's closed cash-ups.
- [ ] An accounts officer can open the revenue summary but not the daily OPD register; a cashier can open neither.
- [ ] Each report for a 31-day range on a facility with 1,500 visits a day returns in under 5 seconds.

**Endpoints:** `GET /reports/opd-register?date=`, `GET /reports/attendance?from=&to=`, `GET /reports/top-diagnoses?from=&to=`, `GET /reports/revenue?from=&to=`, `GET /reports/claims?from=&to=`, `GET /reports/stock`, `GET /reports/break-glass?from=&to=`. Add `&format=csv` for a download.

**Tables:** none of its own; read-only queries over the tables above.

### F15. Deployment, backup and central uplink

**CI from Sprint 1; the rest in Sprint 6.** **Goal:** a facility server can be installed in a day, survives power cuts without losing a committed record, can be rebuilt from backup on new hardware in under 4 hours, and reports its health to the central platform.

**Facility server setup**

1. Hardware (minimum): 4 CPU cores, 16 GB RAM, 512 GB SSD, a second disk for backups, on a UPS with at least 30 minutes of runtime. The network switch and Wi-Fi router are on the UPS too.
2. Ubuntu Server LTS with an encrypted data disk. Docker Compose runs three services: PostgreSQL, the facility-server application, and Nginx (TLS and the built frontend).
3. At install, a facility certificate authority is created; its root certificate is installed on every hospital PC so the browser trusts `https://ereach.local` (assumed hostname).
4. Network UPS Tools watches the UPS and shuts the server down cleanly when the battery runs low.

**Test server from Sprint 1.** The robotics lab's Dell all-in-one (about 64 GB RAM, 1 TB disk) runs the facility server from the first sprint, installed exactly as above. Two adjustments: it has one internal disk, so the backup disk is an external USB drive; and because it is far stronger than the minimum, load tests run with Docker limited to 4 CPUs and 16 GB RAM, so the targets in section 12 are measured on minimum-sized resources.

**Durability settings.** PostgreSQL runs with `fsync = on`, `synchronous_commit = on` and `full_page_writes = on`. These are the defaults; the install script checks them and refuses to start if any is off.

**Backups**

- pgBackRest takes a full backup weekly and a differential backup daily, and archives the write-ahead log continuously to the second disk, giving a local recovery point of about 5 minutes (archive\_timeout is 60 seconds, so the target holds on quiet days too).
- The backup repository on the second disk is itself encrypted by pgBackRest. When the internet is up, a shipper job copies it to the facility's bucket in object storage at the central platform (S3-compatible API required) every 15 minutes, retrying until it succeeds, with a target recovery point of 1 hour; the platform console shows the time of the last good copy (Batch 2, ADR-015).
- A restore drill runs monthly on a spare machine and its result is recorded. A backup that has never been restored is not trusted.

**Central uplink (MVP scope)**

- Every 5 minutes when online, the facility server sends a heartbeat: version, disk space, last backup time, newest audit seal, and how far behind its seal and backup shipping are.
- The newest audit seal hash is shipped as described in F4.
- No live, queryable copy of clinical data is kept centrally in the MVP. Encrypted database backups, which do contain clinical data, are stored centrally; only named platform staff may restore one, every restore is audited, and key custody is decided in Batch 2 (B2-4). The outbox table and its writer are built and tested in Sprint 6, behind a switch that stays off until central sync ships in Phase 2.

**Releases and versions**

- Releases are versioned (`MAJOR.MINOR.PATCH`) and signed. The facility server downloads them; the facility admin chooses when to install.
- Installing takes a backup first, then runs Flyway migrations, then starts the new version. Staff are kept out until a smoke test passes. Until then, rollback means restoring the pre-install backup. Once real clinical data has been written, problems are fixed forward with a new release, never by restoring.
- Migrations follow expand-then-contract (engineering rule 16), so the previous application version still runs on the new schema.
- The central platform accepts heartbeats, seals and backups from the current release and the two before it.

**CI pipeline (Sprint 1)**

On every pull request: build, unit tests, integration tests with Testcontainers, policy tests, Spring Modulith boundary check, frontend tests, and a Flyway migration run against an empty database and against the previous release's schema.

**Acceptance criteria**

- [ ] Pulling the server's power cable during a load test of payments loses no payment that the client saw as successful.
- [ ] A full restore onto a different machine, from local or central backup, completes in under 4 hours and passes audit verification.
- [ ] The central platform shows a facility as unhealthy when its last backup is older than 24 hours or its heartbeat is missing for 30 minutes.
- [ ] Installing release N+1 over N, then rolling back to N from the pre-install backup, leaves the facility working on N.
- [ ] A pull request that breaks a module boundary or a migration fails CI.

**Endpoints (central platform):** `POST /central/v1/heartbeats`, `POST /central/v1/seals`, `GET /central/v1/releases/latest`.

**Tables:** facility side `outbox_entry`, `idempotency_record`; central side `facility_heartbeat`, `received_seal`, `release`.

## 12. Non-functional requirements

These apply to every feature. They are sized for a large teaching-hospital outpatient load on one facility server, which leaves a general hospital headroom; the figures, not the comparison, are the requirement. Workflow times are design targets for a routine case, never clinical time limits. The "reference server" is the minimum hardware in F15.

| Area | Requirement | Target | How we check |
| --- | --- | --- | --- |
| Capacity | Registrations on one facility server | 1.5 million | Load test with synthetic data |
| Capacity | Visits per day | 1,500 | Load test |
| Capacity | Users working at once | 150 | Load test |
| Speed | Patient search | under 300 ms at p95 | Load test on the reference server |
| Speed | Saving a note, order, prescription or payment | under 500 ms at p95 | Load test |
| Speed | Policy decision on one record | under 10 ms at p95 | Microbenchmark in CI |
| Speed | Report over 31 days | under 5 s | Load test |
| Workflow | Register a new patient | under 2 minutes | Timed with real staff in the pilot |
| Workflow | Document a routine consultation | under 90 seconds | Timed in the pilot |
| Workflow | Prescribe and sign | under 20 seconds | Timed in the pilot |
| Availability | Working with no internet | all MVP clinical and cash workflows, for as long as it lasts; excluded are payer verification, off-site backup, release downloads and account changes from the central platform | Exit test with the internet cable unplugged |
| Durability | Committed data lost on sudden power loss | none | Power-pull test (F15) |
| Recovery | Local recovery point | about 5 minutes | Restore drill |
| Recovery | Off-site recovery point when online | 1 hour | Restore drill from central backup |
| Recovery | Rebuild on new hardware | under 4 hours | Restore drill |
| Security | Transport | HTTPS (TLS 1.2 or higher) on the LAN and to the central platform | Configuration test |
| Security | Data at rest | Encrypted data disk; encrypted off-site backups | Install checklist |
| Integrity | Audit sealing | every 60 seconds or 500 events | Integration test |
| Integrity | Seal shipped to central | within 15 minutes of connectivity | Integration test |
| Privacy | Where data lives | Nigeria only, for production data and backups | Hosting contract |
| Privacy | Personal data breach | assessed, and the NDPC notified within 72 hours when required | Written incident runbook |
| Client | Browsers and screens | Chrome or Edge, two latest versions, on PCs with 4 GB RAM at 1366 × 768 | Playwright on that viewport |
| Compatibility | Older facility versions | central accepts current and two previous releases | Contract tests in CI |
| Formats | Display | English; dates as dd/mm/yyyy; naira with two decimals; times in Africa/Lagos | UI review |

## 13. Build order and your first tickets

Six two-week sprints build the MVP, then a two-week pilot. Each sprint ends with something a hospital person could try, and with one more layer of access control.

| Sprint | Features | Access-control layer | Done when |
| --- | --- | --- | --- |
| 1 | Repository, CI, shared building blocks, F1 tenancy, F2 users and sign-in, F4 audit events, F6 registration and search | RBAC and facility scope | A records officer signs in and registers and finds patients; every action is audited |
| 2 | F7 visits and triage, F8 consultation, F4 sealing and verification | ReBAC and break-glass | A patient goes from records to triage to a signed consultation |
| 3 | F9 lab, F10 prescriptions, F5 consent | ABAC | A doctor orders a test and prescribes; the lab returns a verified result |
| 4 | F11 pharmacy and stock, F12 billing and payments | PBAC | The full cash journey works, including the payment gate |
| 5 | F13 payer layer and claims | RuBAC | An insured visit is priced, claimed and exported |
| 6 | F14 reports, F15 backup, uplink, releases and outbox | RAdAC | The exit test in section 3 passes on the reference server |
| Pilot | Install at the hospital, train staff, run alongside paper |  | The pilot metrics are measured for 2 weeks |

### Sprint 1 tickets

Do ER-001 to ER-004 in order; they unblock everything else. After that, ER-005 to ER-012 can run in parallel.

| Ticket | What to build | Spec | Status |
| --- | --- | --- | --- |
| ER-001 | Tag the current code `v1-2023`, move it to `archive/`, create the folder layout | Section 7 | In progress |
| ER-002 | Maven multi-module build, Spring Modulith boundary test, a Testcontainers base test class, SvelteKit app with AGENTS.md, CI running all of it on every pull request | Sections 7 and F15 | In progress |
| ER-003 | `deploy/dev.yml` with PostgreSQL, a dev profile that loads a demo facility and one user per role | Section 7 | In progress |
| ER-004 | `shared`: `Ids.newId()` for UUIDv7 (library `com.github.f4b6a3:uuid-creator`, assumed), `Money` backed by `long` kobo, RFC 9457 error handler, base entity with the standard columns and `row_version` checking | Sections 8, 9, 10 | Not started |
| ER-005 | Idempotency: request filter, `idempotency_record` table, replay of stored responses | Rule 5 | Not started |
| ER-006 | Facility, clinic and settings tables; migration and application database roles; RLS on facility tables; `set_config` transaction hook for `app.facility_id` and `app.user_id`, with its tests | F1 | In progress |
| ER-007 | Users, practitioner profiles, memberships, role assignments, sign-in, MFA, facility switch, idle lock with PIN | F2 | Not started |
| ER-008 | Policy engine version 1: YAML loader, decision context, evaluator, SQL predicate compiler for role and facility conditions, RBAC policies with tests | F3 | Not started |
| ER-009 | Audit events: same-transaction writer for changes, separate-transaction writer for denials, insert-only grants, the every-endpoint-is-audited test | F4 | Not started |
| ER-010 | Registration and search API with duplicate check and hospital number sequence | F6 | Not started |
| ER-011 | Frontend shell: sign-in, facility picker, idle lock screen, layout, API client that adds `Idempotency-Key` and `If-Match` | F2, section 10 | Not started |
| ER-012 | Frontend patient search and registration screens, including the duplicate comparison view | F6 | Not started |

Progress on 17/10/2026. ER-001: the folder layout exists; archiving the 2023 code is still open. ER-002: the multi-module build, the Modulith boundary test and the Testcontainers base class are done and passing; the SvelteKit app, AGENTS.md and CI remain. ER-003: `deploy/dev.yml`, `deploy/dev-init.sql` and migrations V1 (extensions) and V1\_1 (framework tables) are done; the demo facility and users wait for the ER-006 and ER-007 tables. ER-006: the two database roles exist; the facility tables, RLS and the hook remain.

Later sprints get their tickets at sprint planning, written the same way: one ticket per coherent piece, each pointing to its spec.

## 14. Definition of done

A ticket is done when every box is ticked. If one cannot be ticked, say why in the pull request.

- [ ] Every acceptance criterion in the feature spec that the ticket covers passes as an automated test.
- [ ] Unit tests cover the rules; integration tests run against real PostgreSQL; API tests cover each endpoint's success and its main failures.
- [ ] Every new action has a policy file with at least one allow and one deny test.
- [ ] Every new table is created by a Flyway migration, never by Hibernate or a library, and has the standard columns of its record category (section 8), `facility_id` with row-level security enabled and forced if facility-scoped, composite foreign keys to other facility tables, and partial unique indexes for any uniqueness on editable records.
- [ ] Every write endpoint requires `Idempotency-Key`; every update checks `If-Match`.
- [ ] Every new endpoint writes audit events, and the every-endpoint-is-audited test still passes.
- [ ] No money in anything other than integer kobo; no time stored outside `timestamptz`.
- [ ] Migrations are new files only, follow expand-then-contract, and pass the previous-schema check in CI.
- [ ] The frontend screen works at 1366 × 768 in Chrome and has a Playwright test for its main path.
- [ ] This document is updated in the same pull request if anything in it changed.
- [ ] Another person has reviewed the code against section 8.

## 15. Assumptions, open questions and sources

### Assumptions

These were decided without confirmation from the pilot hospital. Each is built as written and can be changed here in one place.

| Area | Assumption | Label |
| --- | --- | --- |
| Pilot site | A private, mission or faith-based hospital with a general-hospital outpatient workflow; public hospitals in Lagos and Ogun already run state record programmes | Low-risk |
| Hosting | Central platform at MTN's Lagos data centre, with S3-compatible object storage; a Lagos alternative if not | Low-risk |
| Stack | Svelte 5 with SvelteKit; GitHub with GitHub Actions; `uuid-creator` for UUIDv7; Ubuntu Server LTS; hostname `ereach.local` | Low-risk |
| Payment gates | Lab and pharmacy need clearance by default; consultation does not; emergencies always skip | Validate before patients |
| Receipts | Gapless numbering on by default | Validate before patients |
| Payments | Whole lines only, no part-payment of a line | Low-risk |
| Sessions | Idle lock after 10 minutes, full sign-in once per 12-hour shift, lock after 5 failed attempts in 15 minutes | Low-risk |
| Step-up and PIN | A step-up lasts 5 minutes; three wrong PINs on a locked session end it | Validate before patients |
| Treatment access | Read-only for 24 hours after the visit closes; ends at once on handover | Validate before patients |
| Break-glass | 60 minutes, reviewed within 7 days | Validate before patients |
| Results | Unacknowledged abnormal results escalate after 24 hours | Validate before patients |
| Risk | Step-up after more than 30 different patients opened in 10 minutes | Low-risk |
| Vital signs | Adult highlighting for ages 18 and over only; unusual values need confirmation | Validate before patients |
| Clinical reference | ICD-10 2019 edition; allergy clash matched on generic name and drug class | Validate before patients |
| Benefit rules | A missing benefit rule means not covered | Validate before patients |
| Payer shortfall | When a payer approves less than claimed, the default is write-off (accounts officer approval), not billing the patient; bundled payment is treated as fee-for-service | Validate before patients |
| Registration | Hospital number `{PREFIX}/{YYYY}/{NNNNNN}`; an age-only date of birth is estimated as 1 July; temporary names for unidentified patients | Low-risk |
| Claims | One generic CSV export profile; payer profiles built from real templates in the pilot | Validate before patients |
| Reports | Age bands 0 to 4, 5 to 14, 15 to 24, 25 to 49, 50 to 64, 65 and above | Low-risk |
| Idempotency | Idempotency keys expire after 24 hours | Low-risk |
| Version support | The central platform accepts each message format for 12 months after its successor ships; facilities are warned 90 days before support ends | Low-risk |
| Devices (Phase 2) | A device caches open visits in its user's clinic and patients seen there in the last 7 days | Low-risk |
| Retention | Audit events kept at least 7 years; an approved disposal process for everything else | Legal |
| Treatment consent | Treatment needs no separate consent record; attendance and the National Health Act cover it | Legal |
| Central backups | Encrypted backups containing clinical data are stored at the central platform | Legal |

### Open questions

- Which payers does the pilot hospital bill most, and in what file layout do they accept claims?
- Who at the pilot hospital will be the clinical champion and the data protection lead?
- What are the pilot hospital's current card and consultation fees, clinic list and hospital number format?

### Sources

- [Determinants of patient waiting time, UDUTH Sokoto (PMC)](https://pmc.ncbi.nlm.nih.gov/articles/PMC3868129)
- [Time-flow study of outpatient services, Rivers State (Nigerian Medical Journal)](https://ajol.info/index.php/nmj/article/view/241625)
- [Lagos State HMIS, HA Convention 2016](https://www.ha.org.hk/haconvention/hac2016/proceedings/downloads/IHF2.2.pdf)
- [Lagos SHIP partnership with Interswitch eClat (Techpoint)](https://techpoint.africa/news/interswitchs-eclat-lagos-health)
- [Ilera Eko enrolment (ThisDay)](https://thisdaylive.com/index.php/2024/12/06/over-1m-people-enrolled-in-lagos-health-insurance-scheme)
- [MTN Sifiso Dabengwa Data Centre (Techpoint)](https://techpoint.africa/news/mtn-largest-data-centre/)
- [RFC 9457, Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457)

## 16. Open clinical, financial, legal and pilot validations

These must be answered by the named people before the matching feature is enabled for real patients. Nobody on the engineering side should invent the answers.

| Area | What to validate | With whom |
| --- | --- | --- |
| Clinical | Vital-sign thresholds and the approach for children; allergy and duplicate-therapy warning scope; result acknowledgement and escalation times; which cadres may verify results and adjust stock; break-glass reasons and duration | Clinical champion, lab head, chief pharmacist |
| Financial | Payment gates; gapless receipts; refund and credit approval; which payers the hospital bills most; each payer's claim template, submission channel, pre-authorisation process, rejection codes, payment cycle, and whether it pays by capitation or fee-for-service; how tariffs are updated | Accounts officer, insurance desk |
| Legal | Retention periods; the lawful basis for treatment without a consent record; a privacy notice that covers central backups; a data processing agreement with the host; whether NDPC registration applies | Legal adviser, data protection lead |
| Pilot | Who owns the hospital and whether a state system already runs there; clinic list, fees and hospital number format; clinical champion and data protection lead; PCs, network and power at the site | Hospital management |
| University (later) | Who is eligible, how eligibility is checked and when it ends; covered services; dependants and visitors; any student or staff records integration; separation of clinical from academic and employment records; reporting needs | Health centre, before any university configuration is built |

## 17. Decisions for Batch 2

Each becomes an architecture decision record before the ERD and schema are drawn. The current lean is a starting point, not a decision.

| ID | Decision | Decision (Batch 2, ADR-004 to ADR-015) |
| --- | --- | --- |
| B2-1 | Person identity: which fields belong to the golden record, who proposes and approves corrections, how a link is confirmed, and how a wrong link is reversed | Facility registrations keep what they captured; corrections are proposals; links are confirmed by an authorised user, and reversing one ends the link without deleting history |
| B2-2 | Platform data authority while a facility is offline | Central owns user accounts, practitioner profiles, ICD-10, drug catalogue, payer registry, benefit rules and consent types, and facilities cache them; facilities own memberships, formulary, price lists and tariffs |
| B2-3 | Multi-version compatibility | Support defined by API, seal, heartbeat and backup formats, not release count; facilities warned well before support ends; a long-offline facility upgrades straight to the latest release, because each release carries the full migration history, unless a release is marked "required stop"; migrations run in a maintenance window with staff kept out |
| B2-4 | Disk encryption, key custody and unattended restart; who may restore central backups | TPM auto-unlock with the server physically secured; restores only by named platform staff, each audited |
| B2-5 | WAL shipping | `archive_timeout` 60 seconds; retries with backlog limits; off-site recovery-point age shown on the platform console while disconnected |
| B2-6 | Financial lifecycle | A visit can be clinically closed while financially open; payer part-payments and rejections move unpaid amounts back to the patient or to write-off by rule (default: write-off, approved by an accounts officer, because many schemes forbid billing enrollees; bundled payment is treated as fee-for-service until bundles are defined); receivables are aged; cash collected, revenue billed and payer receivables are reported separately |
| B2-7 | Result responsibility | The ordering doctor owns a result until acknowledged; handover and cover rotas transfer it; overdue critical results escalate |
| B2-8 | Policy vocabulary and list-versus-record equivalence | Request conditions in memory, row conditions in SQL only; deny as `AND NOT`; break-glass as a relationship; a property test per list endpoint |
| B2-9 | Idempotency in full | Expiry, re-authorisation before replaying a stored response, and step-up handling |
| B2-10 | Device sync boundary | A short record: what a device may cache, which writes it may own, and how revocation works; full design deferred to Phase 2 |
| B2-11 | Audit allowlist and record disposal | Allowlist limited to non-patient settings and configuration values; disposal by an approved, audited job once retention ends |
