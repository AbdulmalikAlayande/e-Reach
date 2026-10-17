# e-Reach Batch 2: System Design

Oct 16, 2026 · @Abdulmalik Alayande

This is how e-Reach is built: one Spring Boot application per hospital, a central platform for backups and seals, and the decisions and database that hold it together. It implements PRD version 1.3; where the PRD and this document disagree, raise it and fix both in the same pull request.

## 1. How to read this

Read this tab top to bottom once: it goes from the whole system down to single requests. Open the other two tabs when a ticket touches a decision or a table.

| Part | Where | Read it when |
| --- | --- | --- |
| Architecture | Section 2 | You need to know what runs where and what talks to what |
| Use cases | Section 3 | You need to know who does what in the MVP |
| OPD workflow | Section 4 | You build any screen in the patient journey |
| Data model (ERD) | Section 5 | You add or change a table |
| Class diagram | Section 6 | You add a class to a module |
| Sequence diagrams | Sections 7 to 12 | You build the request in question |
| Decision records | Decision records | You wonder why something is built the way it is |
| Database schema (SQL) | Database schema | You write a Flyway migration or a query |

Three conventions hold in every picture. Boxes with a blue border are the part the picture is about. Solid arrows are calls made now; dashed arrows are replies, later steps, or Phase 2. Names in code font are real class, table or endpoint names, spelled exactly as in the code.

## 2. Architecture

&#91;embedded content: architecture · hospital network and central platform\]

Everything a patient's care needs sits inside the hospital network, so the central platform can be unreachable for days without stopping a single consultation (ADR-002).

| Component | Runs on | Job | Talks to |
| --- | --- | --- | --- |
| SvelteKit app | Staff PCs (Chrome or Edge) | Every screen; adds the idempotency, row-version, CSRF and facility headers | Nginx, over HTTPS |
| Nginx | Facility server | TLS with the facility's own certificate authority; serves the app's files; passes `/api` to the backend | `facility-server` |
| `facility-server` | Facility server, in Docker | All business logic, the policy engine, audit sealing, escalations, the uplink | PostgreSQL; the central platform |
| PostgreSQL 16 | Facility server, in Docker | The only store of the facility's data; enforces row-level security | pgBackRest (WAL archive) |
| pgBackRest | Facility server | Full, differential and WAL backups to the second disk, encrypted | Second disk |
| Shipper job | Facility server | Copies the encrypted backup repository off-site every 15 minutes when online | Object storage |
| UPS with NUT | Hardware | Keeps the server and network switch up for 30 minutes; shuts the server down cleanly | The server's power |
| `central` | Central platform | Receives heartbeats and seal hashes; publishes signed release bundles | Facility servers |
| Object storage | Central platform | One bucket per facility for encrypted backups | The shipper |
| Tablets | Phase 2 | Offline documentation away from the network (ADR-011) | `facility-server` |

## 3. Use cases

&#91;embedded content: use cases · 12 roles\]

Each row is one role and everything it may do in the MVP; anything not on its row is denied by policy. The full can and cannot list per role is in PRD section 4, and a person who needs two rows gets two roles. Patients are not actors: they never sign in to the MVP.

## 4. OPD workflow

&#91;embedded content: OPD workflow · 4 decisions, 1 payment gate\]

The one decision that touches money is `billing.isClearedForService`: a line is cleared when the visit is an emergency, or when any required pre-authorisation code is recorded, the coverage was active on the service date, and the patient share is paid, zero or waived (PRD F12). Clinical closure does not wait for money: unpaid charges stay on the patient's account and never block the next visit (PRD F7).

## 5. Data model

The schema has 80 tables (plus Spring Session's two), too many for one picture, so it is drawn in three areas. Each box shows a table and its most important columns; the full columns, keys and indexes are in Database schema. Every facility table also has `facility_id`, and every reference between facility tables includes it (ADR-003), so the arrows below are composite keys.

### 5.1 People and access

&#91;embedded content: ERD · people and access\]

Every clinical table points at `patient_registration (facility_id, person_id)`, never at `person` alone, so the database refuses clinical data for someone not registered at that facility.

### 5.2 The clinical journey

&#91;embedded content: ERD · the clinical journey\]

Rows are written once and corrected by adding rows: an encounter gets addenda, a result is superseded, a prescription gets a new version, stock gets a movement. Nothing on this page is ever overwritten after it is signed or verified.

### 5.3 Money and payers

&#91;embedded content: ERD · money and payers\]

A charge's status is never stored. The `charge_status` view derives it from these rows: a void wins, then a waiver, then a zero patient share (covered), then payments not voided (paid); otherwise unpaid (ADR-012). Capitated and institution-funded services create a `utilisation_record` and never a claim line.

## 6. Class diagram

&#91;embedded content: class diagram · 12 public classes\]

These are the public classes each module exposes; everything else in a module sits in its `internal` package (ADR-001). Arrows mean "calls" or "references", in the direction of the dependency, and match the module table in PRD section 7. IDs are typed records (`PersonId`, `VisitId`, `UserId`), so the compiler stops one being passed where another is expected.

## 7. Sequence: registration with duplicate detection

&#91;embedded content: sequence · registration, 15 steps\]

Everything from step 4 to step 14 is one database transaction, so a suspected duplicate (steps 8 and 9) rolls back and leaves nothing behind, not even the idempotency key. Implements PRD F6.

| Step | What happens |
| --- | --- |
| 1 | The app sends `POST /api/v1/registrations` with `Idempotency-Key`, `X-XSRF-TOKEN` and `X-Facility-Context`. |
| 2, 3 | Request-level checks: the user has a role allowed `registration.create` in the active facility. A deny returns 403 here, before any write. |
| 4 | `INSERT INTO idempotency_record … ON CONFLICT DO NOTHING` (ADR-010). A matching completed key replays its stored reply instead. |
| 5 | `RegistrationService.register(cmd)` normalises the phone to E.164, builds `name_search`, and HMACs the NIN if given. |
| 6, 7 | Candidates: same normalised phone, or same NIN hash, or trigram name similarity ≥ 0.5 with compatible sex and birth years within 2. Top 5 by score. |
| 8, 9 | If any candidate exists and `confirmNoDuplicate` is false: 409 `DUPLICATE_SUSPECTED` with the candidates; the transaction rolls back. |
| 10 | `SELECT … FOR UPDATE` on `hospital_number_sequence` for this facility and Lagos year, add one, format `{PREFIX}/{YYYY}/{NNNNNN}`. |
| 11 | Insert `person`, then `patient_registration` (and `person_identifier` rows for NIN or extra phones). |
| 12, 13 | Audit event `registration.create`, plus `duplicate_override` with the reason when confirmed. |
| 14 | The reply is written into the idempotency row; one commit makes all of it permanent together. |
| 15 | 201 with `personId`, `registrationId` and `hospitalNumber`. |

## 8. Sequence: consultation to billing

&#91;embedded content: sequence · consultation to billing, 16 steps\]

Three people act here (doctor, cashier, lab scientist), each in their own request and transaction. A prescription follows the same path as the lab test, except that its charge is raised when the pharmacist quotes it (PRD F11), not when the doctor prescribes.

| Step | What happens |
| --- | --- |
| 1, 2 | The doctor opens the next patient; the encounter is created and the visit moves to `with_doctor`. The visit module has already granted the doctor's `treating_clinician` relationship when the visit was assigned. |
| 3 | Notes save as drafts; at least one ICD-10 diagnosis is required before signing. |
| 4, 5 | `LabService` creates the `lab_order` (responsible user = the ordering doctor) and asks billing for a charge in the same transaction. |
| 6, 7 | `PricingService` finds the benefit rule and tariff in force today, applies the co-payment in basis points, and returns the patient and payer shares with the `tariff_version_id` (PRD F13). A cash visit gets the price-list price, all patient share. |
| 8 | The charge is inserted once and never updated. |
| 9 | The order exists but is not cleared, so the lab worklist shows it as waiting for payment. |
| 10 to 12 | The cashier selects unpaid lines and the receipt is printed from the stored payment, so a reprint keeps its number; billing locks those charge rows, takes the next receipt number in the same transaction, and inserts `payment` and `payment_line` rows. |
| 13 to 15 | When the lab records collection, `LabService` calls `BillingService.isClearedForService`, the only clearance check (PRD F12). Not cleared returns 409 `PAYMENT_REQUIRED`. |
| 16 | The order becomes `collected`; results and verification follow (PRD F9). |

## 9. Sequence: sign-in and an access decision

&#91;embedded content: sequence · sign-in and access decision, 17 steps\]

The shaded part is any later request. The single-record check runs the same SQL predicate a list would use, plus `AND id = :id`, so a list and a single read can never disagree (ADR-008). Implements PRD F2 and F3.

| Step | What happens |
| --- | --- |
| 1 to 3 | `POST /api/v1/auth/sign-in`. `user_account` has no row-level security, so it can be read before any facility is chosen. A locked or suspended account, or a wrong password, stops here; failures count toward the 5-in-15-minutes lock. |
| 4, 5 | The transaction sets `app.user_id`, which the membership policy allows (V10), and lists the user's active, in-date memberships and roles. |
| 6 | A new session is created (any session this browser held is invalidated); the `SESSION` cookie is set. |
| 7, 8 | The user picks a facility; admins enter a TOTP code. The session stores the active membership. With one membership, this step is automatic. |
| 9 | Any later request: the security filters check the session, idle lock, 12-hour limit, CSRF token and `X-Facility-Context`. |
| 10 | The transaction hook runs `set_config('app.facility_id', …, true)` and `set_config('app.user_id', …, true)`. |
| 11 | Request-level conditions first (role, purpose, MFA, risk). A failure here returns 403 or 401 `STEP_UP_REQUIRED`. |
| 12, 13 | Row-level conditions: the compiled predicate (care relationship, authorship, result responsibility, covering, break-glass) with `AND id = :id`. No row means not allowed. |
| 14 to 16 | The denial is audited in its own `REQUIRES_NEW` transaction, so it survives the request's rollback. |
| 17 | 404, so the record's existence is not revealed (PRD section 10). |

## 10. Sequence: the audit write path and sealing

&#91;embedded content: sequence · audit write and sealing, 15 steps\]

A request never waits on sealing: events are plain inserts, and the sealer gathers them later (ADR-009). Implements PRD F4.

| Step | What happens |
| --- | --- |
| 1, 2 | A data change's event is inserted in the same transaction as the change, so both commit or both roll back. `AuditWriter` rejects any `details` key not on the allowlist. |
| 3, 4 | A denial's event is inserted in a `REQUIRES_NEW` transaction, so it survives the failed request. |
| 5, 6 | Per facility: `pg_advisory_xact_lock` so only one sealer runs, then every committed event with no `audit_seal_member` row, ordered by `seq`. |
| 7 | Leaf = SHA-256(0x00 ‖ RFC 8785 canonical JSON of the event). Node = SHA-256(0x01 ‖ left ‖ right); an odd node moves up unchanged. Seal hash = SHA-256(previous seal hash ‖ root ‖ event count as 8 bytes ‖ `sealed_at` in microseconds as 8 bytes). |
| 8 | One transaction inserts the `audit_seal` and one `audit_seal_member` per event. |
| 9 to 12 | The uplink sends the newest seal's number and hash; only after the central platform acknowledges does it insert `audit_seal_shipment`. |
| 13 to 15 | Verification recomputes every leaf, root and seal; compares the newest shipped seal with the central copy; flags events older than 5 minutes with no seal, and events sealed more than 5 minutes after `occurred_at`. |

## 11. Sequence: device to facility server sync (Phase 2)

&#91;embedded content: sequence · device sync, Phase 2\]

Nothing here is built in the MVP. It is drawn now so the MVP's tables, keys and rules already fit it (ADR-011); every participant box is dashed because the whole flow is Phase 2.

| Step | What happens |
| --- | --- |
| 1 | Offline, every change is written to the encrypted local store with an outbox entry: a client-made idempotency key, the base `row_version`, and a hybrid logical clock stamp. |
| 2 to 4 | Pushes go only to the facility server. The device's token must be registered and not revoked, and the user's membership active. |
| 5 to 7 | Each operation is applied once (idempotency key). Clinical facts are appended; signed records accept amendments only; lab results, stock and money are refused from devices; a demographics edit on an out-of-date version goes to a review queue. |
| 8, 9 | The reply lists accepted, rejected and conflicting operations and the server's sequence number; the device clears what was accepted. |
| 10 to 14 | The device pulls changes since its last sequence number, limited to its cache scope: open visits in its user's clinic and patients seen there in the last 7 days. |

## 12. Sequence: facility server to central platform

&#91;embedded content: sequence · facility to central, 13 steps\]

Every connection starts at the hospital, so the facility server needs no open inbound port, and the central platform has no way to change facility data (ADR-002). Implements PRD F15 with ADR-014 and ADR-015.

| Step | What happens |
| --- | --- |
| 1 to 3 | The heartbeat carries the app version, message format versions, reference-data version, free disk space, last backup time, newest seal number and how far behind seal shipping is. The reply names the latest release and any format nearing the end of support. Seal shipping (section 10) runs on the same connection. |
| 4 | PostgreSQL's `archive_command` hands each WAL segment to pgBackRest, which writes it encrypted to the second disk; with `archive_timeout = 60` a segment closes at least every minute. This never needs the internet. |
| 5 to 8 | The shipper copies new encrypted repository files to the facility's bucket, retrying until it succeeds, and reports its last good copy so the platform console can show the off-site recovery point. |
| 9 to 11 | Releases are pulled, never pushed. The installer refuses a bundle whose Ed25519 signature does not match the release key, and refuses to skip a release marked "required stop". |
| 12, 13 | A backup first, then Flyway migrations, a restart and the smoke checks; staff are let back in only after they pass. |

## 13. What this changes in the PRD

Working the design through to tables showed nine places where PRD version 1.2 needed a matching edit. All nine are applied in PRD version 1.3 (17/10/2026). None changes what a user sees; each makes the PRD describe what will actually be built.

| PRD place | Change | Decided in |
| --- | --- | --- |
| F2 | The transaction hook also sets `app.user_id`, so memberships can be listed at sign-in | ADR-003, schema V10 |
| F3 | Policies are read from the release; the database copy (`policy_set`) is the audit record, not the source | ADR-008 |
| F5 | `given_at` and `withdrawn_at` become `decided_at` on append-only consent rows | Schema V5 |
| F9 | The lab order's `cleared` state is computed by billing, not stored | Schema V7 |
| F10, F11 | Prescription items gain status `closed` and a `status_reason` column | Schema V7 |
| F12, section 8 | Charge status is derived from linked rows; the receipt number lives on `payment`, with no separate receipt table | ADR-012 |
| F13 | A payer shortfall defaults to write-off; `bundled` is treated as fee-for-service until bundles are defined | ADR-012 |
| F15 | Off-site backup is a shipper job copying the encrypted pgBackRest repository, not a second pgBackRest repository | ADR-015 |
| Section 17, B2-3 | A facility upgrades straight to the latest release unless one is marked "required stop" | ADR-014 |

New defaults were added to PRD section 15 with their labels. Validate before patients: step-up lasts 5 minutes and three wrong PINs end a session; a payer shortfall defaults to write-off. Low-risk: idempotency keys expire after 24 hours; message formats are supported for 12 months after a successor, with a 90-day warning; devices cache 7 days of patients (Phase 2). The 24-hour escalation of unacknowledged abnormal results was already there.
