# e-Reach decision log

Every decision made since PRD version 1.6 (27/10/2026), plus the earlier ones that were later reversed. Each entry gives the date, the decision, the reason and where to see it in the code or the documents. When a decision is replaced, the old entry stays and is marked **Replaced by**, so the history is never lost.

How to use this log:
- The code and its tests are the final word on what is built. This log records what was decided, including things not built yet.
- When this log and a document disagree, this log is newer. Fix the document in the same change.
- When the log and the code disagree on something already built, stop and ask; do not pick one.
- Add an entry whenever a decision is made. Never edit an old entry's decision; replace it with a new entry.

Status words: **Decided** (agreed, may not be built yet), **Built** (in the code), **Replaced** (no longer holds), **Open** (not decided).

---

## Documents and process

### D-001 · 01/11/2026 · The repository is the original copy of every document · Decided
The Markdown files in `docs/` are the originals from now on. The earlier shared copies (PRD, Batch 2, Test Cases, Setup Guide) stayed as they were on 27/10/2026 and are no longer updated.
**Why:** the code changes in the repository, and git keeps the history; two editable copies would drift.

### D-002 · 31/10/2026 · Build order for the next features · Decided
The order is in `docs/pending-features.md`: platform operator setup, sign-in, facility setup, invitations, accepting an invitation, facility switching, staff management, session safety, then patients.
**Why:** each flow needs a signed-in person from the one before it.

---

## Shared module and errors

### D-003 · 01/11/2026 · `shared` may use Spring and JPA · Built
`shared` holds what both servers (or several modules) need, whatever its dependencies: typed IDs, `Money`, `SystemActors`, `BaseEntity`, the ID converters and the error module.
**Replaces:** PRD 1.5 and ARCHITECTURE.md rule 14, which said `shared` is plain Java with only `uuid-creator`.
**Why:** if something is shared, it belongs in `shared`.
**See:** `server/shared/pom.xml`, `ng.ereach.shared.entities.BaseEntity`.

### D-004 · 01/11/2026 · The error module follows RFC 9457 and lives in `shared` · Built
- Every error is `application/problem+json` with `type`, `title`, `status`, `detail`, `instance`, and the extension `code` (PRD section 10).
- `type` is `https://ereach.ng/problems/<code in kebab case>`.
- `instance` is `urn:uuid:<new UUIDv7>`, written to the log with the error.
- Errors Spring raises itself keep `type: about:blank`, take the HTTP status phrase as `title`, and get the code `HTTP_<status>`.
- Several invalid fields go in one `VALIDATION_FAILED` problem with an `errors` list of field and message, never the rejected value.
- Extension names must start with a letter, use letters, digits and underscores, be at least 3 characters, and not reuse a standard name.
- `shared` holds `ProblemType`, `CommonProblem`, `EReachException`, `RecordNotFoundException`, `EReachProblems` and the Spring handler, registered by auto-configuration. Each module holds its own `ProblemType` enum and exceptions (for example `iam.errors.IamProblem`). `iam` also handles Spring Security's exceptions, because `shared` does not depend on Spring Security.
**Replaces:** the 27/10/2026 suggestion of `urn:ereach:problem:...` type URIs, the draft in `platform/errors`, and PRD 1.5's "the error handler lives in facility-server".
**See:** `ng.ereach.shared.errors`, `ng.ereach.shared.config.ErrorHandlingAutoConfiguration`, `iam.internal.IAMSecurityExceptionHandler`.

### D-005 · 01/11/2026 · HTTP status codes for conditions and idempotency · Built (codes), Decided (use)
- A failed `If-Match` is 412 `STALE_RECORD` (RFC 9110, section 13.1.1). A missing required `If-Match` is 428 `PRECONDITION_REQUIRED` (RFC 6585).
- Idempotency (ER-005), following the IETF Idempotency-Key draft: missing key 400, key reused with a different body 422, first request still running 409.
**Replaces:** older notes that said 409 for a stale row version.
**See:** `ng.ereach.shared.errors.CommonProblem`.

---

## Reference data and settings

### D-006 · 27/10/2026 · States and LGAs come from one repeatable migration · Built
- State codes are ISO 3166-2:NG without `NG-` (for example `LA`, `FC`, `NA`).
- An LGA code is the state code, a hyphen and a two-digit number in alphabetical order of name (`LA-01` is Agege).
- Codes never change once released; a new LGA takes the next free number; rows are never deleted.
- Source: the naijR package's copy of the Wikipedia list, with "Nassarawa", "Fufure" and "Kumi" corrected. 37 states, 774 LGAs.
**See:** `R__states_and_LGAs.sql`.

### D-007 · 27/10/2026 · Two setting defaults corrected to match PRD F1 · Built
`gate.consultation_requires_payment` defaults to false and `lab.verification_required` to true.
**See:** `FacilitySettingKey`.

---

## Accounts, joining and the desktop app

### D-008 · 28/10/2026 · The first platform operator is created during installation · Decided
The installer (a screen or the command line) asks for the operator's name, username, password and PIN and sets up MFA, running as the INSTALLER system actor. It works only when no operator exists yet. Further operators are added by an existing operator. On a new PC an operator simply installs the app and signs in, because accounts live on the server. The installer never asks for facility data.
**Replaces:** PRD F1, where creating a facility also created the first `facility_admin` with a one-time password shown to the operator.

### D-009 · 01/11/2026 · Being a platform operator is a flag on the account · Built (schema)
`user_account.is_platform_operator boolean NOT NULL DEFAULT false`; a system account can never have it; `platform_operator` leaves the `role_assignment.role_code` list.
**Why:** the role belongs to the whole server, not to a facility, and a membership needs a facility. An operator with no membership has no `app.facility_id`, so row-level security already hides all patient data from them.
**Replaces:** `platform_operator` as a facility role assignment.

### D-010 · 28/10/2026 · Staff join a facility by invitation · Decided
The operator or a facility admin invites a person with roles, department, dates and, for clinicians, licence details. The invitation is a single-use, expiring joining code, also sent by email when the internet is up; the email link opens the installed app. The invited person chooses a username and password, sets a PIN and, for admin roles, sets up MFA. Someone who already has an account on the same server joins from the facility switcher with a code and their PIN. The first `facility_admin` of a new facility is invited this way.
**Replaces:** the one-time password shown on screen (PRD F1 step 2, F2 step 3).
**Not yet designed:** the invitation table and endpoints (ER-007).

### D-011 · 28/10/2026 · The client is an installed desktop application · Decided
The SvelteKit UI ships as an installed program with its own installer, running in a borderless window. The wrapper (Tauri, Electron or another) is the developer's choice. For WebAuthn, the window that handles sign-in and MFA must load the facility server's HTTPS address, because a bundled `file://`, `app://` or `tauri.localhost` origin can never match the facility's relying party ID.
**Replaces:** PRD section 7, a PWA installed from Chrome.

### D-012 · 27/10/2026 · Clinician licences are checked in the database · Built
`practitioner_profile` requires a licence body and number, pairs each cadre with its body (doctor with MDCN, nurse and midwife with NMCN, pharmacist and pharmacy technician with PCN, laboratory scientist and technician with MLSCN), and no licence can be held twice.
**See:** V3, `ck_practitioner_profile_body`, `ux_practitioner_profile_licence`.

---

## MFA

### D-013 · 01/11/2026 · Three MFA methods: authenticator app, email code, passkey · Built (schema)
- One `mfa_method` row per method per user; a user may have several. `confirmed_at` is NULL until setup finishes.
- `mfa_required_by` (the 30-day clinician deadline) is on `user_account`, because it is about the person, not a method.
- Email codes need the internet. The rule that everything works offline is relaxed for email: users are told an action needs the internet, and email is allowed.
**Replaces:** B2-13 / PRD F2, which allowed only an authenticator app or a facility hardware key.
**See:** V3, `mfa_method`.

### D-014 · 01/11/2026 · Authenticator app (TOTP) details · Built
SHA-1, 6 digits, 30-second steps, as every common app supports; one step of drift either way; a code from an already used step is refused (`mfa_method.last_used_step`). The 20-byte secret is encrypted with AES-256-GCM using a key from the environment (`EREACH_MFA_ENCRYPTION_KEY`), never hashed, and never logged. The provisioning URI follows Google's Key Uri Format, which is a convention, not an IETF standard.
**See:** `AuthenticatorSecretGenerator`, `MfaSecretCipher`, `MFAService`.

### D-015 · 01/11/2026 · Email codes and WebAuthn challenges share one table · Built (schema)
`mfa_challenge` holds each email code (as an HMAC with a server key, never the code) and each WebAuthn challenge (with the options sent to the client). Rows are short-lived; old ones may be deleted.
**Suggested rules for email codes, not yet built:** six digits, about 10 minutes, five tries, at most one code a minute, a new code consumes the old one.

### D-016 · 01/11/2026 · Passkeys are facility-issued, device-bound security keys · Built (schema)
- WebAuthn with roaming security keys, not Windows Hello: on shared ward PCs, Windows Hello would prove the Windows login, not the clinician.
- A key that can be synced is refused: the BE and BS flags must be false (the database checks this too), attestation is required, and its AAGUID must be on the facility's list of models.
- User verification is required (the key's PIN); credentials are non-discoverable.
- The relying party ID is the facility server's hostname. It must be chosen before the first key is enrolled and never changed, because changing it breaks every key. It must be a name, not an IP address, served over HTTPS with a certificate the PCs trust.
- Each key is a row in `user_security_key`; the `mfa_method` row of type `passkey` is confirmed when the first key is registered.
**Suggested, not decided:** Yubico's `java-webauthn-server` rather than Spring Security's passkey support.
**See:** V3, `user_security_key`.

---

## Data model and mapping

### D-018 · 01/11/2026 · A facility's prefix and national code are unique among live facilities only · Built
`ux_facility_prefix` and `ux_facility_nhfr_code` are partial indexes `WHERE deleted_at IS NULL`, like every other uniqueness rule on an editable table (PRD section 8, rule 7). A soft-deleted facility no longer holds its hospital number prefix or national facility code.
**Replaces:** the full unique indexes in the first V2.
**See:** V2, `PlatformSchemaRulesTests` (TC-006-20, TC-006-21).

### D-019 · 01/11/2026 · Typed IDs are mapped as primary keys through a Hibernate JavaType · Built
Hibernate 7 refuses an `AttributeConverter` on an `@Id`. A primary key is mapped as `@Id @JavaType(FacilityIdJavaType.class) @JdbcTypeCode(SqlTypes.UUID)`. `TypedIdJavaType` in `shared` holds the shared logic, and each typed ID has a small subclass: `FacilityIdJavaType` and `UserIdJavaType` in `shared`, `ClinicIdJavaType` in `platform`. A typed ID in any other attribute keeps its `AttributeConverter`, named with an explicit `@Convert`, because entity scanning does not reach `ng.ereach.shared`, so `autoApply` on the `shared` converters has no effect.
**See:** `ng.ereach.shared.converters.TypedIdJavaType`, `Facility`, `Clinic`, `UserAccount`.

### D-020 · 01/11/2026 · Entities refer to their facility by ID, not by association · Built
`Clinic` and `FacilitySetting` hold `FacilityId facilityId` on the `facility_id` column instead of a `@ManyToOne` to `Facility`. Hibernate 7 cannot load a to-one association lazily when the target entity has `@SoftDelete`, and an eager one would load the facility row with every clinic and setting.
**See:** `Clinic`, `FacilitySetting`.

### D-021 · 01/11/2026 · Usernames are stored as typed and unique ignoring case · Built (schema)
A username belongs to its user, so it is kept exactly as they typed it: 3 to 40 letters (either case), digits, dots, underscores or hyphens. Uniqueness ignores case (`ux_user_account_username` is on `lower(username)`), so two accounts can never differ only by case and look the same on screen or in the audit trail.
**Replaces:** PRD F2's "stored lower-case" and V3's lower-case-only check.
**Still to build:** sign-in and the sign-in throttle must compare usernames ignoring case too, or a changed capital would get round the backoff (ADR-007).
**See:** V3, `UserAccountSchemaRulesTests` (TC-007-27, TC-007-62).

### D-022 · 01/11/2026 · Every person account has an email address · Built (schema)
Email is required for every person, the platform operator included, because an organisation's software needs it for invitations, email codes and notices, even when the server is offline most of the time. It is stored in lower case and belongs to one live account. Phone stays optional. System accounts never have one.
**Replaces:** PRD F2 step 1, "full name, email or phone", and D-008's list of what the installer asks for, which had no email.
**See:** V3 (`ck_user_account_person_email`, `ux_user_account_email`), TC-007-63, TC-007-64.

### D-023 · 01/11/2026 · Nobody gets a temporary password · Built (schema)
The first operator types their own password at the installer, and invited staff choose theirs when they accept (D-010), so no password is ever made for someone else and handed over on paper or by phone. `must_change_password` defaults to false and is set to true only when an admin forces a reset after a suspected leak, in line with NIST SP 800-63B (force a change on evidence of compromise, not by routine). This answers Q-8.
**Replaces:** V3's default of true and its "one-time password" comment.
**See:** V3, `UserAccount`.

### D-024 · 01/11/2026 · The installer step is finished only when the operator's MFA is confirmed · Decided
Until the new operator confirms their first MFA code, the step is not finished: running it again soft-deletes the unconfirmed operator account (and its pending MFA method) and creates the new one. Once a confirmed operator exists, the step is closed for good.
**Why:** an installer who closes the window before scanning the QR code would otherwise leave an operator nobody can sign in as, and the step would refuse to run again.
**See:** TC-007-45.

### D-025 · 01/11/2026 · What an account screen may reveal · Decided
- Sign-in never says whether the username or the password was wrong (PRD F2, one general error).
- Choosing a username is different: the person must learn that it is taken, or they cannot choose another. Those screens are reachable only with the installer's setup code (D-029) or a valid joining code (D-010), so the answer goes only to someone already allowed in. The problem is 409 `USERNAME_TAKEN`, with the detail "That username is not available. Choose another." and never the value typed.
- An invitation to an email that already has an account on the server does not fail: the person is sent to join from the facility switcher (D-010). Only the admin who invites may see that the address already has an account; an anonymous screen never confirms that an email exists.
**See:** TC-007-41.

### D-026 · 01/11/2026 · The installer offers all three MFA methods · Decided
The operator chooses an authenticator app, an email code or a passkey, like every other user. Each method is a separate handler that `MFAService` selects by type, so email and passkeys plug in when they are built without changing the flow. Until then the screen shows them disabled, and the server refuses a request for one with 501 `MFA_METHOD_NOT_AVAILABLE`; an unknown method stays 400 `MFA_METHOD_NOT_SUPPORTED`.
**See:** TC-007-56.

### D-027 · 01/11/2026 · QR codes are drawn with ZXing · Decided
The server draws the authenticator QR code itself (transcript of 01/11/2026) as a PNG, sent as a `data:image/png;base64,` image in `MfaResponse.qrCodeImage`, using `com.google.zxing:core` 3.5.3 (Apache 2.0, no dependencies of its own). The tests decode the same PNG with ZXing to check the provisioning URI inside it. ZXing is understood to be in maintenance mode, taking bug fixes only, which is enough for drawing a QR code; this was not checked against the project's current statement.
**See:** `facility-server/pom.xml`, TC-007-47.

### D-028 · 01/11/2026 · Every staff role needs MFA; elevated roles need it at once · Decided
- At once (`mfa_required_by` is the moment the account is made or the role is given): platform operators, `facility_admin` and `data_protection_lead`, because they control access and the audit trail.
- Within 30 days of getting the role: every other role, including `records_officer`, `cashier`, `accounts_officer`, `insurance_officer` and `medical_director`.
- `mfa_required_by` belongs to the person while roles belong to memberships, so it is the earliest deadline any of their roles sets, and gaining an elevated role moves it to that moment unless MFA is already confirmed.
- Before the deadline a user without MFA has no MFA way past a sign-in backoff (PRD F2). From the deadline, sign-in cannot finish until they set MFA up.
This answers Q-1.
**Replaces:** PRD F2 and ADR-007, which required MFA only for clinical and admin roles.

---

## Tests

### D-017 · 28/10/2026 · Tests empty the V3 tables too · Decided
Every `@BeforeEach` that empties tables must include `role_assignment, facility_membership, user_security_key` before `facility`, because they reference it. `user_account` is never truncated, because that would delete the system actors; tests delete their own person accounts with `DELETE FROM user_account WHERE kind = 'person'`.

---

## Open questions

| # | Question | Raised | Notes |
| --- | --- | --- | --- |
| Q-1 | Does `medical_director` require MFA? | 27/10/2026 | Answered by D-028: yes, within 30 days, like every role that is not elevated |
| Q-2 | Backoff figures: first delay, growth, ceiling | 22/10/2026 | To be set in ER-007 |
| Q-3 | Allowed ranges for `session.idle_lock_minutes` and `session.max_hours` | 27/10/2026 | PRD F1 |
| Q-4 | Which desktop wrapper | 28/10/2026 | Developer's choice; WebAuthn inside its webview must be tested before relying on it |
| Q-5 | Which WebAuthn library | 01/11/2026 | See D-016 |
| Q-6 | Invitation table, code format and expiry | 28/10/2026 | ER-007 |
| Q-7 | Column name for a user reference: V3 uses `user_account_id`, the planned V4 to V9 tables use `user_id` | 01/11/2026 | Pick one before V4 is written |
| Q-8 | Is `user_account.must_change_password` still needed, now that invited staff choose their own password? | 01/11/2026 | Answered by D-023: only for an admin-forced reset |
