# Database schema

> This file in `docs/batch-2/` is the original; the earlier shared copy stopped being updated on 27/10/2026. Where a migration file already exists, the file is the final word and this document describes it.

The full MVP schema for the facility server, grouped into the migrations that will create it. Every statement here is meant to be copied into migrations as written, and every migration ends with the security statements (last section) for the tables it creates.

## Conventions and how to read the SQL

Each section below is one planned Flyway migration. The installer creates the roles and the database; Flyway (running as `ereach_owner`) creates everything else. In development, `deploy/dev-init.sql` creates the two roles and makes `ereach_owner` the owner of the database.

| Migration | Section | Written by |
| --- | --- | --- |
| `V1__extensions.sql` | This section: extensions and default privileges | ER-003, done |
| `V1_1__framework_tables.sql` | Spring Modulith's `event_publication` table and Spring Session's two tables, copied unchanged from each library's own schema file, with `DELETE` granted to `ereach_app` on those three tables | ER-003, done |
| `V2__facility_and_clinic.sql` (planned as `V2__platform.sql`) | Platform and reference tables; the clinical catalogues are not in it yet | ER-006, done |
| `V3__identity_and_access_management.sql` | Identity tables | ER-007, in progress |
| `V4__audit.sql` | Audit tables | ER-009 |
| `V5__person_consent.sql` | Person, registration and consent tables | ER-010 |
| `V6__visit_clinical.sql` | Visit and clinical tables | Sprint 2 |
| `V7__lab_prescribing_pharmacy.sql` | Lab, prescribing and pharmacy tables | Sprints 3 and 4 |
| `V8__payer_billing.sql` | Payer and billing tables | Sprints 4 and 5 |
| `V9__access_claims_sync.sql` | Access, claims, sync and idempotency tables | Several sprints (see below) |
| Security statements | Row-level security, grants and views: not a file, but the statements each migration above applies to its own tables | Every ticket that adds a table |
| `R__*.sql` | Reference data (ICD-10, drugs, lab tests, states, LGAs, payers), loaded with `INSERT … ON CONFLICT DO UPDATE` | The ticket that first needs the data |

Flyway refuses a version lower than one already applied, so the version numbers above are the plan, and the real number is the next free one when a ticket writes its file. When a ticket needs a table from a later section early (for example `idempotency_record` for ER-005 in Sprint 1, which sits in the V9 section), it writes those tables in the next free version, and the plan shifts by one.

**Every table falls into one record category** (PRD section 8, record lifecycles), and its standard columns follow from it:

| Category | Standard columns | The application may |
| --- | --- | --- |
| Editable | `id`, `created_at`, `created_by`, `updated_at`, `updated_by`, `row_version`, `deleted_at` | Insert and update; delete only by setting `deleted_at` |
| Insert-only | `id`, `created_at`, `created_by` | Insert only (enforced by `REVOKE` in the migration that creates each table) |
| Transport | `id`, `created_at` | Insert, update and delete |
| Reference | Its own columns only | Read only; changed by `R__` migrations |

Facility tables also carry `facility_id uuid NOT NULL` and `UNIQUE (facility_id, id)`, and reference other facility tables by `(facility_id, …_id)`. `created_by` and `updated_by` hold a `user_account` ID without a foreign key, so audit columns never block a delete of a test account. Wherever they exist they are NOT NULL and default to app.user\_id, created\_at and updated\_at default to now(), and on editable tables the set\_audit\_columns trigger (V2) keeps them right on every update (PRD section 8, rule 19). Names follow one pattern: tables singular, `ux_` unique, `ix_` index, `ex_` exclusion, `fk_` foreign key.

```sql
-- Run once by the installer as a superuser (psql variables shown with :'name').
CREATE ROLE ereach_owner LOGIN PASSWORD :'owner_password';
CREATE ROLE ereach_app   LOGIN PASSWORD :'app_password';
CREATE DATABASE ereach OWNER ereach_owner;

-- V1__extensions.sql (Flyway, as ereach_owner; the installer grants it CREATE on the database first)
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Every table created by a later migration is readable and writable by the app role
-- from the start; each migration then narrows its own tables by record category
-- (see the security statements at the end).
ALTER DEFAULT PRIVILEGES FOR ROLE ereach_owner IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE ON TABLES TO ereach_app;
ALTER DEFAULT PRIVILEGES FOR ROLE ereach_owner IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO ereach_app;
```

Creating extensions needs more than table ownership; the installer runs `GRANT CREATE ON DATABASE ereach TO ereach_owner;` before Flyway, and on PostgreSQL 16 both extensions are trusted, so the database owner may create them.

## V2: Platform and reference tables

Facilities, their settings and clinics, and the shared catalogues every facility reads. Reference tables are filled by `R__` migrations from CSV files in the repository.

```sql
-- V2__platform.sql

-- Author columns (PRD section 8, rule 19). Runs BEFORE UPDATE on every editable table:
-- who created a row never changes, and every update records who made it. An update
-- made with no user in the context fails on updated_by NOT NULL.
CREATE OR REPLACE FUNCTION set_audit_columns() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  NEW.created_at := OLD.created_at;
  NEW.created_by := OLD.created_by;
  NEW.updated_at := now();
  NEW.updated_by := NULLIF(current_setting('app.user_id', true), '')::uuid;
  RETURN NEW;
END $$;

-- Reference: geography (no author columns and no trigger)
CREATE TABLE state (
  code  text PRIMARY KEY,
  name  text NOT NULL
);

CREATE TABLE lga (
  code        text PRIMARY KEY,
  state_code  text NOT NULL REFERENCES state (code),
  name        text NOT NULL,
  -- Lets facility check that its LGA lies in its state.
  CONSTRAINT ux_lga_state_code UNIQUE (state_code, code)
);

-- Platform: facilities
CREATE TABLE facility (
  id                      uuid PRIMARY KEY,
  name                    text NOT NULL,
  type                    text NOT NULL CHECK (type IN ('phc', 'clinic', 'secondary', 'tertiary')),
  state_code              text NOT NULL REFERENCES state (code),
  lga_code                text NOT NULL,
  CONSTRAINT fk_facility_lga FOREIGN KEY (state_code, lga_code) REFERENCES lga (state_code, code),
  address                 text,
  phone_number            text,
  hospital_number_prefix  text NOT NULL CHECK (hospital_number_prefix ~ '^[A-Z]{2,6}$'),
  -- National facility code (NHFR) and state registration (for example HEFAMAA in Lagos):
  -- optional, stored exactly as written, because the registries publish no fixed format.
  nhfr_code                  text CHECK (btrim(nhfr_code) <> ''),
  state_registration_number  text CHECK (btrim(state_registration_number) <> ''),
  state_registration_agency  text CHECK (btrim(state_registration_agency) <> ''),
  CHECK ((state_registration_number IS NULL) = (state_registration_agency IS NULL)),
  status                  text NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'deactivated')),
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   uuid NOT NULL DEFAULT NULLIF(current_setting('app.user_id', true), '')::uuid,
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   uuid NOT NULL DEFAULT NULLIF(current_setting('app.user_id', true), '')::uuid,
  row_version  bigint NOT NULL DEFAULT 0,
  deleted_at   timestamptz
);
-- Full (not partial) unique: a prefix is never reused, even by a deleted facility.
CREATE UNIQUE INDEX ux_facility_prefix ON facility (hospital_number_prefix) WHERE deleted_at IS NULL;
-- NULLs never clash, so facilities without a national code are allowed.
CREATE UNIQUE INDEX ux_facility_nhfr_code ON facility (nhfr_code) WHERE deleted_at IS NULL;
CREATE TRIGGER tg_facility_audit_columns BEFORE UPDATE ON facility
  FOR EACH ROW EXECUTE FUNCTION set_audit_columns();

-- value is jsonb: true/false for the gate, verification and receipt keys,
-- a whole number for the two session keys (PRD F1).
CREATE TABLE facility_setting (
  id           uuid PRIMARY KEY,
  facility_id  uuid NOT NULL REFERENCES facility (id),
  key          text NOT NULL CHECK (key IN (
                 'gate.consultation_requires_payment', 'gate.lab_requires_payment',
                 'gate.pharmacy_requires_payment', 'lab.verification_required',
                 'session.idle_lock_minutes', 'session.max_hours', 'receipt.gapless')),
  value        jsonb NOT NULL,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   uuid NOT NULL DEFAULT NULLIF(current_setting('app.user_id', true), '')::uuid,
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   uuid NOT NULL DEFAULT NULLIF(current_setting('app.user_id', true), '')::uuid,
  row_version  bigint NOT NULL DEFAULT 0,
  deleted_at   timestamptz,
  CONSTRAINT ux_facility_setting_facility_id UNIQUE (facility_id, id)
);
CREATE UNIQUE INDEX ux_facility_setting_key ON facility_setting (facility_id, key) WHERE deleted_at IS NULL;
CREATE TRIGGER tg_facility_setting_audit_columns BEFORE UPDATE ON facility_setting
  FOR EACH ROW EXECUTE FUNCTION set_audit_columns();

CREATE TABLE clinic (
  id           uuid PRIMARY KEY,
  facility_id  uuid NOT NULL REFERENCES facility (id),
  code         text NOT NULL CHECK (code ~ '^[A-Z0-9_]{2,20}$'),
  name         text NOT NULL,
  status       text NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'inactive')),
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   uuid NOT NULL DEFAULT NULLIF(current_setting('app.user_id', true), '')::uuid,
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   uuid NOT NULL DEFAULT NULLIF(current_setting('app.user_id', true), '')::uuid,
  row_version  bigint NOT NULL DEFAULT 0,
  deleted_at   timestamptz,
  CONSTRAINT ux_clinic_facility_id UNIQUE (facility_id, id)
);
CREATE UNIQUE INDEX ux_clinic_code ON clinic (facility_id, code) WHERE deleted_at IS NULL;
CREATE TRIGGER tg_clinic_audit_columns BEFORE UPDATE ON clinic
  FOR EACH ROW EXECUTE FUNCTION set_audit_columns();

-- Reference: clinical catalogues
CREATE TABLE icd10_code (
  code     text PRIMARY KEY,
  title    text NOT NULL,
  chapter  text NOT NULL
);
CREATE INDEX ix_icd10_code_title ON icd10_code USING gin (lower(title) gin_trgm_ops);

CREATE TABLE drug (
  id               uuid PRIMARY KEY,
  code             text NOT NULL UNIQUE,
  generic_name     text NOT NULL,
  drug_class       text NOT NULL,
  form             text NOT NULL,
  strength         text NOT NULL,
  dispensing_unit  text NOT NULL,
  is_countable     boolean NOT NULL
);
CREATE INDEX ix_drug_generic_name ON drug USING gin (lower(generic_name) gin_trgm_ops);

-- Brand names, so a search for "Amoxil" finds amoxicillin (PRD F10)
CREATE TABLE drug_brand (
  id       uuid PRIMARY KEY,
  drug_id  uuid NOT NULL REFERENCES drug (id),
  name     text NOT NULL,
  CONSTRAINT ux_drug_brand_name UNIQUE (drug_id, name)
);
CREATE INDEX ix_drug_brand_name ON drug_brand USING gin (lower(name) gin_trgm_ops);

CREATE TABLE lab_test (
  id             uuid PRIMARY KEY,
  code           text NOT NULL UNIQUE,
  name           text NOT NULL,
  specimen_type  text NOT NULL
);

CREATE TABLE lab_analyte (
  id           uuid PRIMARY KEY,
  lab_test_id  uuid NOT NULL REFERENCES lab_test (id),
  code         text NOT NULL,
  name         text NOT NULL,
  unit         text,
  value_type   text NOT NULL CHECK (value_type IN ('numeric', 'text')),
  CONSTRAINT ux_lab_analyte_code UNIQUE (lab_test_id, code)
);

CREATE TABLE lab_reference_range (
  id              uuid PRIMARY KEY,
  lab_analyte_id  uuid NOT NULL REFERENCES lab_analyte (id),
  sex             text CHECK (sex IN ('female', 'male')),   -- NULL means both
  age_min_years   integer NOT NULL DEFAULT 0 CHECK (age_min_years >= 0),
  age_max_years   integer CHECK (age_max_years IS NULL OR age_max_years >= age_min_years),
  ref_low         numeric,
  ref_high        numeric,
  CHECK (ref_low IS NOT NULL OR ref_high IS NOT NULL)
);

CREATE TABLE frequency (
  code           text PRIMARY KEY,         -- OD, BD, TDS, QDS, NOCTE, STAT, PRN
  name           text NOT NULL,
  times_per_day  integer CHECK (times_per_day > 0)   -- NULL for STAT and PRN
);

CREATE TABLE consent_type (
  code         text PRIMARY KEY,           -- payer_claim_sharing, sms_contact, cross_facility_sharing
  name         text NOT NULL,
  description  text NOT NULL
);

CREATE TABLE rejection_reason (
  code         text PRIMARY KEY,
  description  text NOT NULL
);
```

```sql
-- End of V2__platform.sql: security statements for the tables above.

-- facility has no facility_id and no row-level security (see below).
ALTER TABLE facility_setting ENABLE ROW LEVEL SECURITY;
ALTER TABLE facility_setting FORCE ROW LEVEL SECURITY;
CREATE POLICY p_facility ON facility_setting
  USING (facility_id = NULLIF(current_setting('app.facility_id', true), '')::uuid)
  WITH CHECK (facility_id = NULLIF(current_setting('app.facility_id', true), '')::uuid);

ALTER TABLE clinic ENABLE ROW LEVEL SECURITY;
ALTER TABLE clinic FORCE ROW LEVEL SECURITY;
CREATE POLICY p_facility ON clinic
  USING (facility_id = NULLIF(current_setting('app.facility_id', true), '')::uuid)
  WITH CHECK (facility_id = NULLIF(current_setting('app.facility_id', true), '')::uuid);

REVOKE INSERT, UPDATE ON
  state, lga, icd10_code, drug, drug_brand, lab_test, lab_analyte, lab_reference_range,
  frequency, consent_type, rejection_reason
FROM ereach_app;
```

`facility` has no `facility_id` and no row-level security: a facility's name and type are not patient data, and sign-in must list every facility a user belongs to before one is chosen. Only `platform_operator` policies allow writing it. The trigram indexes serve the ICD-10 and drug searches in PRD F8 and F10.

## V3: Identity tables

Staff accounts, the four system actors, MFA methods and challenges, sign-in protection, security keys, clinician credentials, memberships and roles (PRD F2, ADR-020, ADR-021). Spring Session's own tables are in V1\_1. The SQL below is the migration file as it stands on 01/11/2026.

D-009 is in the file: `user_account.is_platform_operator`, the system-secrets check that refuses it for a system account, and a `role_code` list without `'platform_operator'`. One decided change is not in the file yet (`docs/decisions/log.md`):

- D-010: an invitation table, designed in ER-007.

Notes on the file: the author columns have no foreign key, as everywhere; `sign_in_throttle` and `mfa_challenge` are written before or during sign-in and carry no author columns; `mfa_challenge` rows may be deleted. Tests that empty tables must include `role_assignment, facility_membership, user_security_key` before `facility`, and never truncate `user_account`, which holds the system actors.

```sql
-- V3__identity_and_access_management.sql
-- 1. User Accounts.
CREATE TABLE user_account (
    id                      UUID PRIMARY KEY,
    kind                    TEXT NOT NULL DEFAULT 'person' CHECK (kind IN ('person', 'system')),
    username                TEXT NOT NULL CHECK (username ~ '^[a-z0-9._-]{3,40}$'),
    full_name               TEXT NOT NULL CHECK (btrim(full_name) <> ''),
    email                   TEXT CHECK (email = lower(email) AND email ~ '^[^@\s]+@[^@\s]+\.[^@\s]+$'),
    phone                   TEXT CHECK (phone ~ '^\+[1-9][0-9]{6,14}$'),
    password_hash           TEXT,
    must_change_password    BOOLEAN NOT NULL DEFAULT true,-- true while the one-time password is in use
    pin_hash                TEXT,
    mfa_required_by             TIMESTAMPTZ,-- end of a clinician's 30-day grace period, they must have a confirmed MFA method by then
    status                TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'suspended')),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by            UUID NOT NULL DEFAULT NULLIF(current_setting('app.user_id', true), '')::UUID,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by            UUID NOT NULL DEFAULT NULLIF(current_setting('app.user_id', true), '')::UUID,
    row_version           BIGINT NOT NULL DEFAULT 0,
    deleted_at            TIMESTAMPTZ,

    -- A person (human) always has a password, but a system actor don't have one.
    CONSTRAINT ck_user_account_password CHECK ((kind = 'person') = (password_hash IS NOT NULL)),

    -- A system actor never has a PIN, an email or an MFA deadline.
    CONSTRAINT ck_user_account_system_secrets
        CHECK (kind = 'person' OR (pin_hash IS NULL AND email IS NULL AND mfa_required_by IS NULL))

    -- If mfa_required_by is not null and its value is lesser than now
);

CREATE UNIQUE INDEX ux_user_account_username ON user_account (username) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX ux_user_account_email ON user_account (email) WHERE deleted_at IS NULL AND email IS NOT NULL;
CREATE TRIGGER tg_user_account_audit_columns BEFORE UPDATE ON user_account
    FOR EACH ROW EXECUTE FUNCTION set_audit_columns();

-- The system actors, with the fixed IDs from SystemActors in the shared module.
INSERT INTO user_account (id, kind, username, full_name, must_change_password, created_by, updated_by)
VALUES
    ('00000000-0000-7000-8000-000000000001', 'system', 'system.installer', 'Installer', false,
     '00000000-0000-7000-8000-000000000001', '00000000-0000-7000-8000-000000000001'),
    ('00000000-0000-7000-8000-000000000002', 'system', 'system.reference_data', 'Reference data', false,
     '00000000-0000-7000-8000-000000000001', '00000000-0000-7000-8000-000000000001'),
    ('00000000-0000-7000-8000-000000000003', 'system', 'system.audit_sealer', 'Audit sealer', false,
     '00000000-0000-7000-8000-000000000001', '00000000-0000-7000-8000-000000000001'),
    ('00000000-0000-7000-8000-000000000004', 'system', 'system.outbox_sender', 'Outbox sender', false,
     '00000000-0000-7000-8000-000000000001', '00000000-0000-7000-8000-000000000001');


/*
    2. Mfa Method:

    Users are able to verify with multiple sources of mfa, yes it breaks our offline rule, but our rule is stupid in the
    first place, we should not force them at all, informing them that an action will require their device to be connected
    to the internet is a good idea, then since the app runs on their device it works fine, but we only support verification
    by email, passkeys (or hardware keys, just the secure one) and authenticator app.
 */
CREATE TABLE mfa_method (
    id                  UUID PRIMARY KEY,
    user_account_id     UUID NOT NULL REFERENCES user_account (id),
    type                VARCHAR(15) NOT NULL CHECK ( type IN ('email', 'totp', 'passkey')),
    secret_encrypted    BYTEA,          -- authenticator only: the TOTP seed, encrypted with the installer's key
    last_used_step      BIGINT,         -- authenticator only: 30-second step of the last accepted code, so no code works twice
    confirmed_at        TIMESTAMPTZ,    -- NULL until the user enters a first correct code (MFA is fully set up)
    last_used_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          UUID NOT NULL DEFAULT NULLIF(current_setting('app.user_id', true), '')::UUID,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by          UUID NOT NULL DEFAULT NULLIF(current_setting('app.user_id', true), '')::UUID,
    row_version         BIGINT NOT NULL DEFAULT 0,
    deleted_at          TIMESTAMPTZ,

    -- An authenticator method should always have a secret, an email method does not need one and does not use steps.
    -- Same goes for passkeys (a passkey's keys live in user_security_key)
    CONSTRAINT ck_mfa_method_shape CHECK (
        (type = 'totp' AND secret_encrypted IS NOT NULL)
            OR (type IN ('email', 'passkey') AND secret_encrypted IS NULL AND last_used_step IS NULL))
);

CREATE UNIQUE INDEX ux_mfa_method_user_type ON mfa_method (user_account_id, type) WHERE deleted_at IS NULL;
CREATE TRIGGER tg_mfa_method_audit_columns BEFORE UPDATE ON mfa_method
    FOR EACH ROW EXECUTE FUNCTION set_audit_columns();


/*
    3. MFA challenges:

    Each email code that was sent, or each WebAuthn challenge issued., it is a transport table, so rows are short-lived,
    and has no author columns, which means old rows can be fully deleted too (or maybe soft deleted).
*/

CREATE TABLE mfa_challenge (
    id               UUID PRIMARY KEY,
    mfa_method_id    UUID NOT NULL REFERENCES mfa_method (id),
    kind             TEXT NOT NULL CHECK (kind IN ('email_code', 'webauthn')),
    purpose          TEXT NOT NULL CHECK (purpose IN ('enrolment', 'sign_in', 'step_up')),
    code_hash        BYTEA,            -- email only: HMAC of the code with a server key, never the code itself
    request_options  JSONB,            -- WebAuthn only: the creation or request options sent to the client
    expires_at       TIMESTAMPTZ NOT NULL,
    attempt_count    INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    consumed_at      TIMESTAMPTZ,      -- set when used, or when replaced by a newer one
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_mfa_challenge_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_mfa_challenge_shape CHECK (
        (kind = 'email_code' AND code_hash IS NOT NULL AND request_options IS NULL)
            OR (kind = 'webauthn' AND code_hash IS NULL AND request_options IS NOT NULL))
);

CREATE INDEX ix_mfa_challenge_method ON mfa_challenge (mfa_method_id, created_at);
GRANT DELETE ON mfa_challenge TO ereach_app;

/*
    4. Sign In Throttle:
    Failed sign-ins per username per PC, basically exists as a measure of security and protection for the sign-in feature
*/
CREATE TABLE sign_in_throttle (
    id                 UUID PRIMARY KEY,
    username           TEXT NOT NULL,
    client_ip          inet NOT NULL,
    failed_count       integer NOT NULL DEFAULT 0 CHECK (failed_count >= 0),
    first_failed_at    TIMESTAMPTZ,
    last_failed_at     TIMESTAMPTZ,
    blocked_until      TIMESTAMPTZ,                   -- password-only attempts from this PC wait until then...
    mfa_failed_count   integer NOT NULL DEFAULT 0 CHECK (mfa_failed_count >= 0),
    mfa_blocked_until  TIMESTAMPTZ,                   -- the password-plus-MFA way has its own limit
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ux_sign_in_throttle_user_ip UNIQUE (username, client_ip)
);

-- Finds one account failing from many PCs, so the facility admin can be alerted.
CREATE INDEX ix_sign_in_throttle_username ON sign_in_throttle (username, last_failed_at);


/*
    5. User Security Key:
    Hardware keys the facility issues, one row per registered key. Only the public key is stored.
    Keys are device-bound: a credential that can be synced is refused.
*/
CREATE TABLE user_security_key (
    id                     UUID PRIMARY KEY,
    user_account_id        UUID NOT NULL REFERENCES user_account (id),
    rp_id                  TEXT NOT NULL CHECK (btrim(rp_id) <> ''),   -- the facility hostname the key was registered for
    credential_id          BYTEA NOT NULL CHECK (octet_length(credential_id) BETWEEN 1 AND 1023),
    public_key_cose        BYTEA NOT NULL,                             -- COSE-encoded public key
    cose_algorithm         INTEGER NOT NULL,                           -- for example -7 (ES256) or -8 (EdDSA)
    aaguid                 UUID NOT NULL,                              -- the key model, checked against the facility's allow list
    attestation_format     TEXT NOT NULL CHECK (attestation_format <> 'none'),
    transports             TEXT[] NOT NULL DEFAULT '{}'
       CHECK (transports <@ ARRAY['usb', 'nfc', 'ble', 'smart-card', 'hybrid', 'internal']::TEXT[]),
    backup_eligible        BOOLEAN NOT NULL DEFAULT false CHECK (backup_eligible = false),  -- BE flag: a syncable key is refused
    backup_state           BOOLEAN NOT NULL DEFAULT false CHECK (backup_state = false),     -- BS flag
    sign_count             BIGINT NOT NULL DEFAULT 0 CHECK (sign_count >= 0),
    label                  TEXT NOT NULL CHECK (btrim(label) <> ''),
    issued_by_facility_id  UUID NOT NULL REFERENCES facility (id),
    last_used_at           TIMESTAMPTZ,
    revoked_at             TIMESTAMPTZ,
    revoked_by             UUID REFERENCES user_account (id),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by             UUID NOT NULL DEFAULT NULLIF(current_setting('app.user_id', true), '')::UUID,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by             UUID NOT NULL DEFAULT NULLIF(current_setting('app.user_id', true), '')::UUID,
    row_version            BIGINT NOT NULL DEFAULT 0,
    deleted_at             TIMESTAMPTZ,
    CONSTRAINT ux_user_security_key_credential UNIQUE (credential_id),
    CONSTRAINT ck_user_security_key_revoked CHECK ((revoked_at IS NULL) = (revoked_by IS NULL))
);

CREATE INDEX ix_user_security_key_user ON user_security_key (user_account_id) WHERE revoked_at IS NULL;
CREATE TRIGGER tg_user_security_key_audit_columns BEFORE UPDATE ON user_security_key
    FOR EACH ROW EXECUTE FUNCTION set_audit_columns();



-- 6. Clinician credentials
CREATE TABLE practitioner_profile (
    id              UUID PRIMARY KEY,
    user_account_id         UUID NOT NULL REFERENCES user_account (id),
    cadre           TEXT NOT NULL CHECK (cadre IN (
        'doctor', 'nurse', 'midwife', 'pharmacist', 'pharmacy_technician',
        'medical_laboratory_scientist', 'medical_laboratory_technician')),
    licence_body    TEXT NOT NULL CHECK (licence_body IN ('MDCN', 'NMCN', 'PCN', 'MLSCN')),
    licence_number  TEXT NOT NULL CHECK (btrim(licence_number) <> ''),
    specialty       TEXT CHECK (btrim(specialty) <> ''),   -- optional cos many nurses and pharmacists have none
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by   UUID NOT NULL DEFAULT NULLIF(current_setting('app.user_id', true), '')::UUID,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by   UUID NOT NULL DEFAULT NULLIF(current_setting('app.user_id', true), '')::UUID,
    row_version  BIGINT NOT NULL DEFAULT 0,
    deleted_at   TIMESTAMPTZ,
    -- Each cadre is licensed by one body, and a doctor cannot hold a PCN license, same way a pharmacist cannot hold a MDCN license.
    CONSTRAINT ck_practitioner_profile_body CHECK (
        (cadre = 'doctor' AND licence_body = 'MDCN')
            OR (cadre IN ('nurse', 'midwife') AND licence_body = 'NMCN')
            OR (cadre IN ('pharmacist', 'pharmacy_technician') AND licence_body = 'PCN')
            OR (cadre IN ('medical_laboratory_scientist', 'medical_laboratory_technician') AND licence_body = 'MLSCN')
    )
);

CREATE UNIQUE INDEX ux_practitioner_profile_user ON practitioner_profile (user_account_id) WHERE deleted_at IS NULL;
-- It is impossible for two same people to have or own the same license.
CREATE UNIQUE INDEX ux_practitioner_profile_licence
    ON practitioner_profile (licence_body, licence_number) WHERE deleted_at IS NULL;
CREATE TRIGGER tg_practitioner_profile_audit_columns BEFORE UPDATE ON practitioner_profile
    FOR EACH ROW EXECUTE FUNCTION set_audit_columns();



/*
    7. Facility Membership

    A user (practitioner) usually works at a facility, for a period of time. And they work at multiple facilities at once.
    So it is possible for a user (practitioner) to hold multiple facility memberships
*/
CREATE TABLE facility_membership (
    id           UUID PRIMARY KEY,
    facility_id  UUID NOT NULL REFERENCES facility (id),
    user_account_id      UUID NOT NULL REFERENCES user_account (id),
    department   TEXT,
    status       TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'suspended', 'ended')),
    valid_from   date NOT NULL,
    valid_to     date CHECK (valid_to IS NULL OR valid_to >= valid_from),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by   UUID NOT NULL DEFAULT NULLIF(current_setting('app.user_id', true), '')::UUID,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by   UUID NOT NULL DEFAULT NULLIF(current_setting('app.user_id', true), '')::UUID,
    row_version  BIGINT NOT NULL DEFAULT 0,
    deleted_at   TIMESTAMPTZ,
    CONSTRAINT ux_facility_membership_facility_id UNIQUE (facility_id, id)
);

CREATE UNIQUE INDEX ux_facility_membership_user ON facility_membership (facility_id, user_account_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_facility_membership_user ON facility_membership (user_account_id);
CREATE TRIGGER tg_facility_membership_audit_columns BEFORE UPDATE ON facility_membership
    FOR EACH ROW EXECUTE FUNCTION set_audit_columns();

/*
    Row Level Security statements for Facility memberships:
    Memberships are only visible in the active facility, or to their own user (for sign-in, before a facility is chosen)
 */
ALTER TABLE facility_membership ENABLE ROW LEVEL SECURITY;
ALTER TABLE facility_membership FORCE ROW LEVEL SECURITY;
CREATE POLICY p_facility_membership ON facility_membership
    USING (facility_id = NULLIF(current_setting('app.facility_id', true), '')::UUID
        OR user_account_id = NULLIF(current_setting('app.user_id', true), '')::UUID)
    WITH CHECK (facility_id = NULLIF(current_setting('app.facility_id', true), '')::UUID);



/*
    8.Role Assignment:

    Roles held under a facility membership. Removing a role sets deleted_at for that row, which makes sense,
    since it takes effect on subsequent requests, as it will not be fetched as part of the roles attached to that membership
*/
CREATE TABLE role_assignment (
    id             UUID PRIMARY KEY,
    facility_id    UUID NOT NULL,
    membership_id  UUID NOT NULL,
    role_code      TEXT NOT NULL CHECK (role_code IN (
        'records_officer', 'nurse', 'doctor', 'lab_scientist', 'pharmacist', 'cashier',
        'accounts_officer', 'insurance_officer', 'facility_admin', 'medical_director',
        'data_protection_lead')
    ),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by   UUID NOT NULL DEFAULT NULLIF(current_setting('app.user_id', true), '')::UUID,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by   UUID NOT NULL DEFAULT NULLIF(current_setting('app.user_id', true), '')::UUID,
    row_version  BIGINT NOT NULL DEFAULT 0,
    deleted_at   TIMESTAMPTZ,

    CONSTRAINT ux_role_assignment_facility_id UNIQUE (facility_id, id),

    -- The role belongs to a membership in the same facility.
    CONSTRAINT fk_role_assignment_membership FOREIGN KEY (facility_id, membership_id) REFERENCES facility_membership (facility_id, id)
);

CREATE UNIQUE INDEX ux_role_assignment_role ON role_assignment (membership_id, role_code) WHERE deleted_at IS NULL;
CREATE TRIGGER tg_role_assignment_audit_columns BEFORE UPDATE ON role_assignment
    FOR EACH ROW EXECUTE FUNCTION set_audit_columns();

/*
    Row Level Security statements for role assignments:
 */
ALTER TABLE role_assignment ENABLE ROW LEVEL SECURITY;
ALTER TABLE role_assignment FORCE ROW LEVEL SECURITY;
CREATE POLICY p_role_assignment ON role_assignment
    USING (facility_id = NULLIF(current_setting('app.facility_id', true), '')::UUID
        OR membership_id IN (
            SELECT m.id FROM facility_membership m
            WHERE m.user_account_id = NULLIF(current_setting('app.user_id', true), '')::UUID))
    WITH CHECK (facility_id = NULLIF(current_setting('app.facility_id', true), '')::UUID);
```

A role is a job; a cadre is a qualification. Policies use both: only a user with the `lab_scientist` role **and** the `medical_laboratory_scientist` cadre may verify a result (PRD F9). Platform operators are not a facility role: being an operator is a flag on the account (D-009), so an operator holds no membership and sees no facility data.

## V4: Audit tables

All four are insert-only (ADR-009). `occurred_at` and `actor_user_id` serve as the event's `created_at` and `created_by`. `person_id` has no foreign key on purpose: an audit row must never block, or be blocked by, the data it describes.

```sql
-- V4__audit.sql

CREATE TABLE audit_event (
  id              uuid PRIMARY KEY,
  facility_id     uuid NOT NULL REFERENCES facility (id),
  seq             bigint GENERATED ALWAYS AS IDENTITY,
  occurred_at     timestamptz NOT NULL DEFAULT now(),
  actor_user_id   uuid NOT NULL,          -- a person, or a system actor for jobs (PRD section 8, rule 19)
  actor_roles     text[] NOT NULL DEFAULT '{}',
  session_id      text,
  action          text NOT NULL,          -- for example 'encounter.read'
  resource_type   text NOT NULL,
  resource_id     uuid,
  person_id       uuid,
  purpose         text NOT NULL,
  decision        text NOT NULL CHECK (decision IN ('allow', 'deny', 'step_up')),
  policy_ids      text[] NOT NULL DEFAULT '{}',
  policy_version  text NOT NULL,          -- the policy set checksum (ADR-008)
  break_glass     boolean NOT NULL DEFAULT false,
  client_ip       inet,
  details         jsonb NOT NULL DEFAULT '{}'::jsonb,   -- allowlisted keys only (ADR-009)
  CONSTRAINT ux_audit_event_seq UNIQUE (seq),
  CONSTRAINT ux_audit_event_facility_id UNIQUE (facility_id, id)
);
CREATE INDEX ix_audit_event_person ON audit_event (facility_id, person_id, occurred_at);
CREATE INDEX ix_audit_event_actor ON audit_event (facility_id, actor_user_id, occurred_at);

CREATE TABLE audit_seal (
  id              uuid PRIMARY KEY,
  facility_id     uuid NOT NULL REFERENCES facility (id),
  seal_no         bigint NOT NULL CHECK (seal_no > 0),
  merkle_root     bytea NOT NULL CHECK (length(merkle_root) = 32),
  prev_seal_hash  bytea NOT NULL CHECK (length(prev_seal_hash) = 32),
  seal_hash       bytea NOT NULL CHECK (length(seal_hash) = 32),
  event_count     integer NOT NULL CHECK (event_count > 0),
  sealed_at       timestamptz NOT NULL,
  created_at      timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ux_audit_seal_no UNIQUE (facility_id, seal_no),
  CONSTRAINT ux_audit_seal_facility_id UNIQUE (facility_id, id)
);

-- event_id is the primary key, so an event can belong to one seal only.
CREATE TABLE audit_seal_member (
  event_id     uuid PRIMARY KEY,
  facility_id  uuid NOT NULL,
  seal_id      uuid NOT NULL,
  CONSTRAINT fk_audit_seal_member_seal FOREIGN KEY (facility_id, seal_id)
    REFERENCES audit_seal (facility_id, id),
  CONSTRAINT fk_audit_seal_member_event FOREIGN KEY (facility_id, event_id)
    REFERENCES audit_event (facility_id, id)
);
CREATE INDEX ix_audit_seal_member_seal ON audit_seal_member (facility_id, seal_id);

CREATE TABLE audit_seal_shipment (
  id              uuid PRIMARY KEY,
  facility_id     uuid NOT NULL,
  seal_id         uuid NOT NULL,
  shipped_at      timestamptz NOT NULL,
  central_ack_at  timestamptz NOT NULL,   -- a row is written only once the central platform acknowledges
  created_at      timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ux_audit_seal_shipment_facility_id UNIQUE (facility_id, id),
  CONSTRAINT fk_audit_seal_shipment_seal FOREIGN KEY (facility_id, seal_id)
    REFERENCES audit_seal (facility_id, id)
);
```

The sealer finds unsealed events with `NOT EXISTS (SELECT 1 FROM audit_seal_member m WHERE m.event_id = e.id)`; the primary key on `event_id` makes that lookup an index probe.

## V5: Person, registration and consent tables

The identity model of ADR-004. `patient_registration` is the Patient entity; its `UNIQUE (facility_id, person_id)` is what every clinical table's foreign key points at, so no clinical row can exist for someone not registered at that facility.

```sql
-- V5__person_consent.sql

-- Platform: a person is only an ID (ADR-004). Insert-only.
CREATE TABLE person (
  id          uuid PRIMARY KEY,
  created_at  timestamptz NOT NULL DEFAULT now(),
  created_by  uuid
);

-- Platform, used from Phase 2: links between duplicate persons. Never a merge.
CREATE TABLE person_link (
  id                   uuid PRIMARY KEY,
  person_id            uuid NOT NULL REFERENCES person (id),
  surviving_person_id  uuid NOT NULL REFERENCES person (id),
  status               text NOT NULL CHECK (status IN ('proposed', 'confirmed', 'reversed')),
  reason               text NOT NULL,
  confirmed_by         uuid,
  confirmed_at         timestamptz,
  reversed_by          uuid,
  reversed_at          timestamptz,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   uuid,
  updated_at   timestamptz,
  updated_by   uuid,
  row_version  bigint NOT NULL DEFAULT 0,
  deleted_at   timestamptz,
  CHECK (person_id <> surviving_person_id)
);

CREATE TABLE patient_registration (
  id                        uuid PRIMARY KEY,
  facility_id               uuid NOT NULL REFERENCES facility (id),
  person_id                 uuid NOT NULL REFERENCES person (id),
  hospital_number           text NOT NULL,
  legacy_hospital_number    text,
  first_name                text NOT NULL,
  middle_name               text,
  last_name                 text NOT NULL,
  name_search               text NOT NULL,   -- lower-case, accents removed, set by the application
  sex                       text NOT NULL CHECK (sex IN ('female', 'male', 'unknown')),
  date_of_birth             date NOT NULL,
  dob_estimated             boolean NOT NULL DEFAULT false,
  is_unidentified           boolean NOT NULL DEFAULT false,
  phone_e164                text CHECK (phone_e164 ~ '^\+[1-9][0-9]{6,14}$'),
  address_line              text,
  state_code                text REFERENCES state (code),
  lga_code                  text REFERENCES lga (code),
  next_of_kin_name          text,
  next_of_kin_phone_e164    text CHECK (next_of_kin_phone_e164 ~ '^\+[1-9][0-9]{6,14}$'),
  next_of_kin_relationship  text,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   uuid,
  updated_at   timestamptz,
  updated_by   uuid,
  row_version  bigint NOT NULL DEFAULT 0,
  deleted_at   timestamptz,
  CONSTRAINT ux_patient_registration_facility_id UNIQUE (facility_id, id),
  -- Full uniques: one registration per person per facility, and a hospital number is never reused.
  CONSTRAINT ux_patient_registration_person UNIQUE (facility_id, person_id),
  CONSTRAINT ux_patient_registration_number UNIQUE (facility_id, hospital_number)
);
CREATE INDEX ix_patient_registration_name ON patient_registration USING gin (name_search gin_trgm_ops);
CREATE INDEX ix_patient_registration_phone ON patient_registration (facility_id, phone_e164);
CREATE INDEX ix_patient_registration_legacy ON patient_registration (facility_id, legacy_hospital_number);

-- One counter row per facility per year. Updated inside the registration transaction,
-- so a rolled-back registration uses no number and numbers have no gaps.
CREATE TABLE hospital_number_sequence (
  facility_id  uuid NOT NULL REFERENCES facility (id),
  year         integer NOT NULL,
  last_value   bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (facility_id, year)
);

CREATE TABLE person_identifier (
  id                uuid PRIMARY KEY,
  facility_id       uuid NOT NULL,
  person_id         uuid NOT NULL,
  type              text NOT NULL CHECK (type IN (
                      'phone', 'nin', 'insurance_member', 'legacy_hospital_number',
                      'matriculation', 'staff_number')),
  value_normalised  text,      -- searchable value; always NULL for NIN
  value_hash        bytea,     -- HMAC-SHA256 with a server key; used for NIN matching
  value_encrypted   bytea,     -- AES-GCM; used to show a NIN to authorised users
  verified_at       timestamptz,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   uuid,
  updated_at   timestamptz,
  updated_by   uuid,
  row_version  bigint NOT NULL DEFAULT 0,
  deleted_at   timestamptz,
  CONSTRAINT ux_person_identifier_facility_id UNIQUE (facility_id, id),
  CONSTRAINT fk_person_identifier_registration FOREIGN KEY (facility_id, person_id)
    REFERENCES patient_registration (facility_id, person_id),
  CHECK (type <> 'nin' OR (value_normalised IS NULL AND value_hash IS NOT NULL AND value_encrypted IS NOT NULL)),
  CHECK (type = 'nin' OR value_normalised IS NOT NULL)
);
CREATE INDEX ix_person_identifier_value ON person_identifier (facility_id, type, value_normalised) WHERE deleted_at IS NULL;
CREATE INDEX ix_person_identifier_hash ON person_identifier (facility_id, type, value_hash) WHERE deleted_at IS NULL;

-- Insert-only: each row is one decision. The newest row for a type, grantee and scope is current.
CREATE TABLE consent_record (
  id                   uuid PRIMARY KEY,
  facility_id          uuid NOT NULL,
  person_id            uuid NOT NULL,
  consent_type_code    text NOT NULL REFERENCES consent_type (code),
  grantee_facility_id  uuid NOT NULL REFERENCES facility (id),
  purpose              text NOT NULL,
  scope                jsonb NOT NULL DEFAULT '{}'::jsonb,   -- for payer_claim_sharing: {"payerId": "…"}
  script_version       text NOT NULL,
  status               text NOT NULL CHECK (status IN ('given', 'refused', 'withdrawn')),
  decided_at           timestamptz NOT NULL,
  created_at           timestamptz NOT NULL DEFAULT now(),
  created_by           uuid,
  CONSTRAINT ux_consent_record_facility_id UNIQUE (facility_id, id),
  CONSTRAINT fk_consent_record_registration FOREIGN KEY (facility_id, person_id)
    REFERENCES patient_registration (facility_id, person_id)
);
CREATE INDEX ix_consent_record_current ON consent_record
  (facility_id, person_id, consent_type_code, grantee_facility_id, decided_at DESC);
```

PRD F5 names `given_at` and `withdrawn_at`. Because consent is append-only, both are the `decided_at` of the row whose status is `given` or `withdrawn`; the `consent_current` view, created at the end of V5, returns the newest decision. Where a registration is deleted, its hospital number stays taken, because the unique constraints are not partial.

## V6: Visit and clinical tables

Visits, vitals, consultations, diagnoses and allergies (PRD F7 and F8). The foreign key from `visit.coverage_id` to `coverage` is added in V8, once `coverage` exists.

```sql
-- V6__visit_clinical.sql

CREATE TABLE visit (
  id                  uuid PRIMARY KEY,
  facility_id         uuid NOT NULL,
  person_id           uuid NOT NULL,
  clinic_id           uuid NOT NULL,
  visit_type          text NOT NULL CHECK (visit_type IN ('new', 'follow_up', 'emergency')),
  payment_mode        text NOT NULL CHECK (payment_mode IN ('cash', 'insurance')),
  coverage_id         uuid,
  status              text NOT NULL DEFAULT 'open' CHECK (status IN (
                        'open', 'triaged', 'with_doctor', 'awaiting_services', 'closed',
                        'left_without_being_seen', 'cancelled')),
  priority            text CHECK (priority IN ('emergency', 'urgent', 'routine')),
  assigned_doctor_id  uuid REFERENCES user_account (id),
  opened_at           timestamptz NOT NULL,
  triaged_at          timestamptz,
  closed_at           timestamptz,
  close_reason        text,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   uuid,
  updated_at   timestamptz,
  updated_by   uuid,
  row_version  bigint NOT NULL DEFAULT 0,
  deleted_at   timestamptz,
  CONSTRAINT ux_visit_facility_id UNIQUE (facility_id, id),
  CONSTRAINT fk_visit_registration FOREIGN KEY (facility_id, person_id)
    REFERENCES patient_registration (facility_id, person_id),
  CONSTRAINT fk_visit_clinic FOREIGN KEY (facility_id, clinic_id) REFERENCES clinic (facility_id, id),
  CHECK ((payment_mode = 'cash' AND coverage_id IS NULL) OR (payment_mode = 'insurance' AND coverage_id IS NOT NULL))
);
-- At most one clinically open visit per person per facility (PRD F7).
CREATE UNIQUE INDEX ux_visit_one_open ON visit (facility_id, person_id)
  WHERE status IN ('open', 'triaged', 'with_doctor', 'awaiting_services') AND deleted_at IS NULL;
CREATE INDEX ix_visit_queue ON visit (facility_id, clinic_id, status, opened_at);
CREATE INDEX ix_visit_doctor ON visit (facility_id, assigned_doctor_id, status);

CREATE TABLE observation (
  id                 uuid PRIMARY KEY,
  facility_id        uuid NOT NULL,
  person_id          uuid NOT NULL,
  visit_id           uuid NOT NULL,
  code               text NOT NULL CHECK (code IN (
                       'temperature', 'pulse', 'respiratory_rate', 'bp_systolic', 'bp_diastolic',
                       'spo2', 'weight', 'height')),
  value_numeric      numeric(7, 2) NOT NULL,
  unit               text NOT NULL,
  recorded_at        timestamptz NOT NULL,
  unusual_confirmed  boolean NOT NULL DEFAULT false,   -- the nurse confirmed an unusual but possible value
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   uuid,
  updated_at   timestamptz,
  updated_by   uuid,
  row_version  bigint NOT NULL DEFAULT 0,
  deleted_at   timestamptz,
  CONSTRAINT ux_observation_facility_id UNIQUE (facility_id, id),
  CONSTRAINT fk_observation_registration FOREIGN KEY (facility_id, person_id)
    REFERENCES patient_registration (facility_id, person_id),
  CONSTRAINT fk_observation_visit FOREIGN KEY (facility_id, visit_id) REFERENCES visit (facility_id, id)
);
CREATE INDEX ix_observation_person ON observation (facility_id, person_id, recorded_at DESC);

CREATE TABLE encounter (
  id                    uuid PRIMARY KEY,
  facility_id           uuid NOT NULL,
  visit_id              uuid NOT NULL,
  person_id             uuid NOT NULL,
  doctor_user_id        uuid NOT NULL REFERENCES user_account (id),
  presenting_complaint  text,
  history               text,
  examination           text,
  assessment            text,
  plan                  text,
  disposition           text CHECK (disposition IN (
                          'discharged', 'follow_up', 'admission_requested', 'referred_out',
                          'left_against_advice')),
  follow_up_date        date,
  disposition_note      text,
  signed_at             timestamptz,
  signed_by             uuid REFERENCES user_account (id),
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   uuid,
  updated_at   timestamptz,
  updated_by   uuid,
  row_version  bigint NOT NULL DEFAULT 0,
  deleted_at   timestamptz,
  CONSTRAINT ux_encounter_facility_id UNIQUE (facility_id, id),
  CONSTRAINT fk_encounter_visit FOREIGN KEY (facility_id, visit_id) REFERENCES visit (facility_id, id),
  CONSTRAINT fk_encounter_registration FOREIGN KEY (facility_id, person_id)
    REFERENCES patient_registration (facility_id, person_id),
  CHECK (disposition IS DISTINCT FROM 'follow_up' OR follow_up_date IS NOT NULL),
  CHECK ((signed_at IS NULL) = (signed_by IS NULL))
);
CREATE UNIQUE INDEX ux_encounter_visit ON encounter (facility_id, visit_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_encounter_person ON encounter (facility_id, person_id, created_at DESC);

-- Insert-only.
CREATE TABLE encounter_addendum (
  id              uuid PRIMARY KEY,
  facility_id     uuid NOT NULL,
  encounter_id    uuid NOT NULL,
  body            text NOT NULL,
  author_user_id  uuid NOT NULL REFERENCES user_account (id),
  signed_at       timestamptz NOT NULL,
  created_at      timestamptz NOT NULL DEFAULT now(),
  created_by      uuid,
  CONSTRAINT ux_encounter_addendum_facility_id UNIQUE (facility_id, id),
  CONSTRAINT fk_encounter_addendum_encounter FOREIGN KEY (facility_id, encounter_id)
    REFERENCES encounter (facility_id, id)
);

CREATE TABLE condition (
  id            uuid PRIMARY KEY,
  facility_id   uuid NOT NULL,
  encounter_id  uuid NOT NULL,
  person_id     uuid NOT NULL,
  icd10_code    text NOT NULL REFERENCES icd10_code (code),
  status        text NOT NULL CHECK (status IN ('provisional', 'confirmed')),
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   uuid,
  updated_at   timestamptz,
  updated_by   uuid,
  row_version  bigint NOT NULL DEFAULT 0,
  deleted_at   timestamptz,
  CONSTRAINT ux_condition_facility_id UNIQUE (facility_id, id),
  CONSTRAINT fk_condition_encounter FOREIGN KEY (facility_id, encounter_id) REFERENCES encounter (facility_id, id),
  CONSTRAINT fk_condition_registration FOREIGN KEY (facility_id, person_id)
    REFERENCES patient_registration (facility_id, person_id)
);
CREATE UNIQUE INDEX ux_condition_code ON condition (facility_id, encounter_id, icd10_code) WHERE deleted_at IS NULL;

CREATE TABLE allergy (
  id              uuid PRIMARY KEY,
  facility_id     uuid NOT NULL,
  person_id       uuid NOT NULL,
  substance       text NOT NULL,
  substance_kind  text NOT NULL CHECK (substance_kind IN ('drug_generic', 'drug_class', 'other')),
  reaction        text,
  severity        text NOT NULL CHECK (severity IN ('mild', 'moderate', 'severe')),
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   uuid,
  updated_at   timestamptz,
  updated_by   uuid,
  row_version  bigint NOT NULL DEFAULT 0,
  deleted_at   timestamptz,
  CONSTRAINT ux_allergy_facility_id UNIQUE (facility_id, id),
  CONSTRAINT fk_allergy_registration FOREIGN KEY (facility_id, person_id)
    REFERENCES patient_registration (facility_id, person_id)
);
CREATE INDEX ix_allergy_person ON allergy (facility_id, person_id) WHERE deleted_at IS NULL;
```

A signed encounter keeps its row: after `signed_at` is set, the application refuses changes to the note columns (PRD F8), and corrections go into `encounter_addendum`. Allergy clash checks compare `substance` with the drug's `generic_name` when `substance_kind` is `drug_generic`, and with its `drug_class` when it is `drug_class`.

## V7: Lab, prescribing and pharmacy tables

Orders, results and their acknowledgement (PRD F9); prescriptions (F10); the formulary and stock ledger (F11). The lab order's `cleared` state in PRD F9 is not stored: it is computed by `billing.isClearedForService` from the order's charge, so it can never disagree with the money.

```sql
-- V7__lab_prescribing_pharmacy.sql

CREATE TABLE formulary_item (
  id             uuid PRIMARY KEY,
  facility_id    uuid NOT NULL REFERENCES facility (id),
  drug_id        uuid NOT NULL REFERENCES drug (id),
  code           text NOT NULL,
  reorder_level  integer NOT NULL DEFAULT 0 CHECK (reorder_level >= 0),
  status         text NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'inactive')),
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   uuid,
  updated_at   timestamptz,
  updated_by   uuid,
  row_version  bigint NOT NULL DEFAULT 0,
  deleted_at   timestamptz,
  CONSTRAINT ux_formulary_item_facility_id UNIQUE (facility_id, id)
);
CREATE UNIQUE INDEX ux_formulary_item_code ON formulary_item (facility_id, code) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX ux_formulary_item_drug ON formulary_item (facility_id, drug_id) WHERE deleted_at IS NULL;

CREATE TABLE lab_order (
  id                   uuid PRIMARY KEY,
  facility_id          uuid NOT NULL,
  encounter_id         uuid NOT NULL,
  person_id            uuid NOT NULL,
  lab_test_id          uuid NOT NULL REFERENCES lab_test (id),
  priority             text NOT NULL CHECK (priority IN ('routine', 'urgent')),
  clinical_note        text,
  status               text NOT NULL DEFAULT 'ordered' CHECK (status IN (
                         'ordered', 'collected', 'resulted', 'verified', 'cancelled')),
  ordered_by           uuid NOT NULL REFERENCES user_account (id),
  responsible_user_id  uuid NOT NULL REFERENCES user_account (id),   -- ADR-013
  specimen_type        text,
  collected_at         timestamptz,
  collected_by         uuid REFERENCES user_account (id),
  cancelled_reason     text,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   uuid,
  updated_at   timestamptz,
  updated_by   uuid,
  row_version  bigint NOT NULL DEFAULT 0,
  deleted_at   timestamptz,
  CONSTRAINT ux_lab_order_facility_id UNIQUE (facility_id, id),
  CONSTRAINT fk_lab_order_encounter FOREIGN KEY (facility_id, encounter_id) REFERENCES encounter (facility_id, id),
  CONSTRAINT fk_lab_order_registration FOREIGN KEY (facility_id, person_id)
    REFERENCES patient_registration (facility_id, person_id),
  CHECK (status <> 'cancelled' OR cancelled_reason IS NOT NULL)
);
CREATE INDEX ix_lab_order_worklist ON lab_order (facility_id, status, priority, created_at);
CREATE INDEX ix_lab_order_responsible ON lab_order (facility_id, responsible_user_id);

-- Insert-only. A correction is a new row whose supersedes_result_id points at the old one.
CREATE TABLE lab_result (
  id                    uuid PRIMARY KEY,
  facility_id           uuid NOT NULL,
  lab_order_id          uuid NOT NULL,
  lab_analyte_id        uuid NOT NULL REFERENCES lab_analyte (id),
  value_numeric         numeric,
  value_text            text,
  unit                  text,
  ref_low               numeric,
  ref_high              numeric,
  abnormal_flag         text NOT NULL CHECK (abnormal_flag IN ('normal', 'low', 'high', 'abnormal')),
  supersedes_result_id  uuid,
  created_at            timestamptz NOT NULL DEFAULT now(),
  created_by            uuid,
  CONSTRAINT ux_lab_result_facility_id UNIQUE (facility_id, id),
  CONSTRAINT fk_lab_result_order FOREIGN KEY (facility_id, lab_order_id) REFERENCES lab_order (facility_id, id),
  CONSTRAINT fk_lab_result_supersedes FOREIGN KEY (facility_id, supersedes_result_id)
    REFERENCES lab_result (facility_id, id),
  CHECK (value_numeric IS NOT NULL OR value_text IS NOT NULL)
);
CREATE INDEX ix_lab_result_order ON lab_result (facility_id, lab_order_id);

-- Insert-only. Verifies the order's current results.
CREATE TABLE lab_result_verification (
  id            uuid PRIMARY KEY,
  facility_id   uuid NOT NULL,
  lab_order_id  uuid NOT NULL,
  created_at    timestamptz NOT NULL DEFAULT now(),
  created_by    uuid NOT NULL,   -- the verifying scientist
  CONSTRAINT ux_lab_result_verification_facility_id UNIQUE (facility_id, id),
  CONSTRAINT fk_lab_result_verification_order FOREIGN KEY (facility_id, lab_order_id)
    REFERENCES lab_order (facility_id, id)
);

-- Insert-only. Opening a result (seen) is not acknowledging it (ADR-013).
CREATE TABLE lab_result_view (
  id             uuid PRIMARY KEY,
  facility_id    uuid NOT NULL,
  lab_result_id  uuid NOT NULL,
  created_at     timestamptz NOT NULL DEFAULT now(),
  created_by     uuid NOT NULL,   -- the viewer
  CONSTRAINT ux_lab_result_view_facility_id UNIQUE (facility_id, id),
  CONSTRAINT fk_lab_result_view_result FOREIGN KEY (facility_id, lab_result_id) REFERENCES lab_result (facility_id, id)
);

CREATE TABLE result_acknowledgement (
  id            uuid PRIMARY KEY,
  facility_id   uuid NOT NULL,
  lab_order_id  uuid NOT NULL,
  note          text,
  created_at    timestamptz NOT NULL DEFAULT now(),
  created_by    uuid NOT NULL,   -- the acknowledging doctor
  CONSTRAINT ux_result_acknowledgement_facility_id UNIQUE (facility_id, id),
  CONSTRAINT fk_result_acknowledgement_order FOREIGN KEY (facility_id, lab_order_id)
    REFERENCES lab_order (facility_id, id)
);
CREATE INDEX ix_result_acknowledgement_order ON result_acknowledgement (facility_id, lab_order_id);

CREATE TABLE prescription (
  id                   uuid PRIMARY KEY,
  facility_id          uuid NOT NULL,
  encounter_id         uuid NOT NULL,
  person_id            uuid NOT NULL,
  version              integer NOT NULL DEFAULT 1 CHECK (version > 0),
  previous_version_id  uuid,
  status               text NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'signed', 'superseded', 'completed')),
  signed_at            timestamptz,
  signed_by            uuid REFERENCES user_account (id),
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   uuid,
  updated_at   timestamptz,
  updated_by   uuid,
  row_version  bigint NOT NULL DEFAULT 0,
  deleted_at   timestamptz,
  CONSTRAINT ux_prescription_facility_id UNIQUE (facility_id, id),
  CONSTRAINT fk_prescription_encounter FOREIGN KEY (facility_id, encounter_id) REFERENCES encounter (facility_id, id),
  CONSTRAINT fk_prescription_registration FOREIGN KEY (facility_id, person_id)
    REFERENCES patient_registration (facility_id, person_id),
  CONSTRAINT fk_prescription_previous FOREIGN KEY (facility_id, previous_version_id)
    REFERENCES prescription (facility_id, id),
  CHECK ((previous_version_id IS NULL) = (version = 1)),
  CHECK ((signed_at IS NULL) = (status = 'draft'))
);
-- A version has at most one successor.
CREATE UNIQUE INDEX ux_prescription_successor ON prescription (facility_id, previous_version_id)
  WHERE previous_version_id IS NOT NULL;

CREATE TABLE prescription_item (
  id                       uuid PRIMARY KEY,
  facility_id              uuid NOT NULL,
  prescription_id          uuid NOT NULL,
  formulary_item_id        uuid NOT NULL,
  dose                     numeric(10, 3) NOT NULL CHECK (dose > 0),
  dose_unit                text NOT NULL,
  route                    text NOT NULL,
  frequency_code           text NOT NULL REFERENCES frequency (code),
  duration_days            integer CHECK (duration_days > 0),
  quantity                 integer NOT NULL CHECK (quantity > 0),
  instructions             text,
  allergy_override_reason  text,
  status                   text NOT NULL DEFAULT 'pending' CHECK (status IN (
                             'pending', 'quoted', 'partly_dispensed', 'dispensed', 'closed', 'cancelled')),
  quantity_quoted          integer CHECK (quantity_quoted > 0),
  quantity_dispensed       integer NOT NULL DEFAULT 0 CHECK (quantity_dispensed >= 0),
  status_reason            text,   -- why it was cancelled or closed
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   uuid,
  updated_at   timestamptz,
  updated_by   uuid,
  row_version  bigint NOT NULL DEFAULT 0,
  deleted_at   timestamptz,
  CONSTRAINT ux_prescription_item_facility_id UNIQUE (facility_id, id),
  CONSTRAINT fk_prescription_item_prescription FOREIGN KEY (facility_id, prescription_id)
    REFERENCES prescription (facility_id, id),
  CONSTRAINT fk_prescription_item_formulary FOREIGN KEY (facility_id, formulary_item_id)
    REFERENCES formulary_item (facility_id, id),
  CHECK (quantity_dispensed <= quantity),
  CHECK (status NOT IN ('cancelled', 'closed') OR status_reason IS NOT NULL)
);
CREATE INDEX ix_prescription_item_prescription ON prescription_item (facility_id, prescription_id);

CREATE TABLE stock_batch (
  id                 uuid PRIMARY KEY,
  facility_id        uuid NOT NULL,
  formulary_item_id  uuid NOT NULL,
  batch_number       text NOT NULL,
  expiry_date        date NOT NULL,
  quantity_on_hand   integer NOT NULL CHECK (quantity_on_hand >= 0),   -- cached; the ledger is the truth
  unit_cost_kobo     bigint NOT NULL CHECK (unit_cost_kobo >= 0),
  supplier           text,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   uuid,
  updated_at   timestamptz,
  updated_by   uuid,
  row_version  bigint NOT NULL DEFAULT 0,
  deleted_at   timestamptz,
  CONSTRAINT ux_stock_batch_facility_id UNIQUE (facility_id, id),
  CONSTRAINT fk_stock_batch_formulary FOREIGN KEY (facility_id, formulary_item_id)
    REFERENCES formulary_item (facility_id, id)
);
CREATE UNIQUE INDEX ux_stock_batch_number ON stock_batch (facility_id, formulary_item_id, batch_number)
  WHERE deleted_at IS NULL;
CREATE INDEX ix_stock_batch_expiry ON stock_batch (facility_id, formulary_item_id, expiry_date)
  WHERE quantity_on_hand > 0;

-- Insert-only ledger.
CREATE TABLE stock_movement (
  id              uuid PRIMARY KEY,
  facility_id     uuid NOT NULL,
  stock_batch_id  uuid NOT NULL,
  type            text NOT NULL CHECK (type IN ('receipt', 'dispense', 'adjustment', 'expiry_write_off')),
  quantity        integer NOT NULL CHECK (quantity <> 0),   -- positive in, negative out
  reason          text,
  reference_type  text,   -- for example 'dispense'
  reference_id    uuid,
  created_at      timestamptz NOT NULL DEFAULT now(),
  created_by      uuid,
  CONSTRAINT ux_stock_movement_facility_id UNIQUE (facility_id, id),
  CONSTRAINT fk_stock_movement_batch FOREIGN KEY (facility_id, stock_batch_id) REFERENCES stock_batch (facility_id, id),
  CHECK ((type = 'receipt' AND quantity > 0)
      OR (type IN ('dispense', 'expiry_write_off') AND quantity < 0)
      OR type = 'adjustment'),
  CHECK (type NOT IN ('adjustment', 'expiry_write_off') OR reason IS NOT NULL)
);
CREATE INDEX ix_stock_movement_batch ON stock_movement (facility_id, stock_batch_id);

-- Insert-only.
CREATE TABLE dispense (
  id                    uuid PRIMARY KEY,
  facility_id           uuid NOT NULL,
  prescription_item_id  uuid NOT NULL,
  stock_batch_id        uuid NOT NULL,
  stock_movement_id     uuid NOT NULL,
  quantity              integer NOT NULL CHECK (quantity > 0),
  created_at            timestamptz NOT NULL DEFAULT now(),
  created_by            uuid,
  CONSTRAINT ux_dispense_facility_id UNIQUE (facility_id, id),
  CONSTRAINT fk_dispense_item FOREIGN KEY (facility_id, prescription_item_id)
    REFERENCES prescription_item (facility_id, id),
  CONSTRAINT fk_dispense_batch FOREIGN KEY (facility_id, stock_batch_id) REFERENCES stock_batch (facility_id, id),
  CONSTRAINT fk_dispense_movement FOREIGN KEY (facility_id, stock_movement_id)
    REFERENCES stock_movement (facility_id, id)
);
```

PRD F10 and F11 add one item status, `closed`: the pharmacist ends an item when the patient will not return for the rest. `status_reason` holds the reason for both `closed` and `cancelled`. A signed prescription's items change only in their status and quantity columns, never their clinical content.

## V8: Payer and billing tables

Payers and their rules (PRD F13), the facility's services and prices, and every money record (F12, ADR-012). From `charge` onward every table is insert-only, except `cash_up` (open until closed) and the `receipt_number_sequence` counter. An insured line the plan does not cover is stored as a cash line: `coverage_id` and `reimbursement_type` are NULL and the price comes from the price list.

```sql
-- V8__payer_billing.sql

-- Platform, reference (shipped in releases, ADR-005)
CREATE TABLE payer (
  id    uuid PRIMARY KEY,
  code  text NOT NULL UNIQUE,
  name  text NOT NULL,
  type  text NOT NULL CHECK (type IN ('nhia', 'hmo', 'state_scheme', 'government_programme', 'institution'))
);

CREATE TABLE payer_plan (
  id        uuid PRIMARY KEY,
  payer_id  uuid NOT NULL REFERENCES payer (id),
  code      text NOT NULL,
  name      text NOT NULL,
  CONSTRAINT ux_payer_plan_code UNIQUE (payer_id, code)
);

CREATE TABLE benefit_rule (
  id                      uuid PRIMARY KEY,
  plan_id                 uuid NOT NULL REFERENCES payer_plan (id),
  service_category        text NOT NULL CHECK (service_category IN ('card', 'consultation', 'lab', 'drug', 'procedure')),
  reference_code          text,     -- a lab_test or drug code; NULL means the whole category
  reimbursement_type      text NOT NULL CHECK (reimbursement_type IN (
                            'fee_for_service', 'capitated', 'bundled', 'institution_funded')),
  covered                 boolean NOT NULL,
  copay_bps               integer CHECK (copay_bps BETWEEN 0 AND 10000),
  copay_fixed_kobo        bigint CHECK (copay_fixed_kobo >= 0),
  max_quantity_per_visit  integer CHECK (max_quantity_per_visit > 0),
  requires_preauth        boolean NOT NULL DEFAULT false,
  effective_from          date NOT NULL,
  effective_to            date CHECK (effective_to IS NULL OR effective_to > effective_from),
  CHECK (copay_bps IS NULL OR copay_fixed_kobo IS NULL),
  CONSTRAINT ex_benefit_rule_period EXCLUDE USING gist (
    plan_id WITH =, service_category WITH =, (coalesce(reference_code, '')) WITH =,
    daterange(effective_from, effective_to, '[)') WITH &&)
);

-- Facility: the billable services and their cash prices
CREATE TABLE service (
  id                 uuid PRIMARY KEY,
  facility_id        uuid NOT NULL REFERENCES facility (id),
  code               text NOT NULL,   -- CARD, CONSULT-GOPD, a lab test code, a formulary code, PAYER-SHORTFALL
  name               text NOT NULL,
  category           text NOT NULL CHECK (category IN (
                       'card', 'consultation', 'lab', 'drug', 'procedure', 'payer_shortfall')),
  clinic_id          uuid,
  lab_test_id        uuid REFERENCES lab_test (id),
  formulary_item_id  uuid,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   uuid,
  updated_at   timestamptz,
  updated_by   uuid,
  row_version  bigint NOT NULL DEFAULT 0,
  deleted_at   timestamptz,
  CONSTRAINT ux_service_facility_id UNIQUE (facility_id, id),
  CONSTRAINT fk_service_clinic FOREIGN KEY (facility_id, clinic_id) REFERENCES clinic (facility_id, id),
  CONSTRAINT fk_service_formulary FOREIGN KEY (facility_id, formulary_item_id)
    REFERENCES formulary_item (facility_id, id),
  CHECK ((category = 'consultation') = (clinic_id IS NOT NULL)),
  CHECK ((category = 'lab') = (lab_test_id IS NOT NULL)),
  CHECK ((category = 'drug') = (formulary_item_id IS NOT NULL))
);
CREATE UNIQUE INDEX ux_service_code ON service (facility_id, code) WHERE deleted_at IS NULL;

CREATE TABLE price_list_item (
  id              uuid PRIMARY KEY,
  facility_id     uuid NOT NULL,
  service_id      uuid NOT NULL,
  price_kobo      bigint NOT NULL CHECK (price_kobo >= 0),
  effective_from  date NOT NULL,
  effective_to    date CHECK (effective_to IS NULL OR effective_to > effective_from),
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   uuid,
  updated_at   timestamptz,
  updated_by   uuid,
  row_version  bigint NOT NULL DEFAULT 0,
  deleted_at   timestamptz,
  CONSTRAINT ux_price_list_item_facility_id UNIQUE (facility_id, id),
  CONSTRAINT fk_price_list_item_service FOREIGN KEY (facility_id, service_id) REFERENCES service (facility_id, id),
  CONSTRAINT ex_price_list_item_period EXCLUDE USING gist (
    facility_id WITH =, service_id WITH =, daterange(effective_from, effective_to, '[)') WITH &&)
    WHERE (deleted_at IS NULL)
);

CREATE TABLE tariff_version (
  id              uuid PRIMARY KEY,
  facility_id     uuid NOT NULL,
  payer_id        uuid NOT NULL REFERENCES payer (id),
  service_id      uuid NOT NULL,
  price_kobo      bigint NOT NULL CHECK (price_kobo >= 0),
  effective_from  date NOT NULL,
  effective_to    date CHECK (effective_to IS NULL OR effective_to > effective_from),
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   uuid,
  updated_at   timestamptz,
  updated_by   uuid,
  row_version  bigint NOT NULL DEFAULT 0,
  deleted_at   timestamptz,
  CONSTRAINT ux_tariff_version_facility_id UNIQUE (facility_id, id),
  CONSTRAINT fk_tariff_version_service FOREIGN KEY (facility_id, service_id) REFERENCES service (facility_id, id),
  CONSTRAINT ex_tariff_version_period EXCLUDE USING gist (
    facility_id WITH =, payer_id WITH =, service_id WITH =,
    daterange(effective_from, effective_to, '[)') WITH &&)
    WHERE (deleted_at IS NULL)
);

CREATE TABLE coverage (
  id                       uuid PRIMARY KEY,
  facility_id              uuid NOT NULL,
  person_id                uuid NOT NULL,
  plan_id                  uuid NOT NULL REFERENCES payer_plan (id),
  member_number            text NOT NULL,
  member_relationship      text NOT NULL CHECK (member_relationship IN ('principal', 'dependant')),
  principal_member_number  text,
  valid_from               date NOT NULL,
  valid_to                 date CHECK (valid_to IS NULL OR valid_to >= valid_from),
  status                   text NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'suspended', 'expired')),
  verified_at              timestamptz,
  verified_by              uuid REFERENCES user_account (id),
  verification_method      text CHECK (verification_method IN ('card', 'portal', 'phone')),
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   uuid,
  updated_at   timestamptz,
  updated_by   uuid,
  row_version  bigint NOT NULL DEFAULT 0,
  deleted_at   timestamptz,
  CONSTRAINT ux_coverage_facility_id UNIQUE (facility_id, id),
  CONSTRAINT fk_coverage_registration FOREIGN KEY (facility_id, person_id)
    REFERENCES patient_registration (facility_id, person_id),
  CHECK ((member_relationship = 'dependant') = (principal_member_number IS NOT NULL))
);
CREATE UNIQUE INDEX ux_coverage_person_plan ON coverage (facility_id, person_id, plan_id) WHERE deleted_at IS NULL;

ALTER TABLE visit ADD CONSTRAINT fk_visit_coverage
  FOREIGN KEY (facility_id, coverage_id) REFERENCES coverage (facility_id, id);

-- Insert-only from here on, except cash_up.
CREATE TABLE charge (
  id                  uuid PRIMARY KEY,
  facility_id         uuid NOT NULL,
  visit_id            uuid NOT NULL,
  person_id           uuid NOT NULL,
  service_id          uuid NOT NULL,
  source_type         text NOT NULL CHECK (source_type IN ('visit', 'lab_order', 'prescription_item', 'claim_line')),
  source_id           uuid NOT NULL,
  quantity            integer NOT NULL CHECK (quantity > 0),
  unit_price_kobo     bigint NOT NULL CHECK (unit_price_kobo >= 0),
  amount_kobo         bigint NOT NULL CHECK (amount_kobo >= 0),
  patient_kobo        bigint NOT NULL CHECK (patient_kobo >= 0),
  payer_kobo          bigint NOT NULL CHECK (payer_kobo >= 0),
  coverage_id         uuid,
  reimbursement_type  text CHECK (reimbursement_type IN (
                        'fee_for_service', 'capitated', 'bundled', 'institution_funded')),
  price_list_item_id  uuid,
  tariff_version_id   uuid,
  requires_preauth    boolean NOT NULL DEFAULT false,
  replaces_charge_id  uuid,     -- set when a line is converted to cash, or for a payer shortfall
  created_at          timestamptz NOT NULL DEFAULT now(),
  created_by          uuid,
  CONSTRAINT ux_charge_facility_id UNIQUE (facility_id, id),
  CONSTRAINT fk_charge_visit FOREIGN KEY (facility_id, visit_id) REFERENCES visit (facility_id, id),
  CONSTRAINT fk_charge_registration FOREIGN KEY (facility_id, person_id)
    REFERENCES patient_registration (facility_id, person_id),
  CONSTRAINT fk_charge_service FOREIGN KEY (facility_id, service_id) REFERENCES service (facility_id, id),
  CONSTRAINT fk_charge_coverage FOREIGN KEY (facility_id, coverage_id) REFERENCES coverage (facility_id, id),
  CONSTRAINT fk_charge_price FOREIGN KEY (facility_id, price_list_item_id) REFERENCES price_list_item (facility_id, id),
  CONSTRAINT fk_charge_tariff FOREIGN KEY (facility_id, tariff_version_id) REFERENCES tariff_version (facility_id, id),
  CONSTRAINT fk_charge_replaces FOREIGN KEY (facility_id, replaces_charge_id) REFERENCES charge (facility_id, id),
  CHECK (amount_kobo = unit_price_kobo * quantity),
  CHECK (patient_kobo + payer_kobo = amount_kobo),
  -- A payer-shortfall charge (source 'claim_line') takes its amount from the payer's response, not a price.
  CHECK (price_list_item_id IS NOT NULL OR tariff_version_id IS NOT NULL OR source_type = 'claim_line'),
  CHECK ((coverage_id IS NULL) = (reimbursement_type IS NULL))
);
CREATE INDEX ix_charge_visit ON charge (facility_id, visit_id);
CREATE INDEX ix_charge_source ON charge (facility_id, source_type, source_id);

CREATE TABLE charge_preauth (
  id            uuid PRIMARY KEY,
  facility_id   uuid NOT NULL,
  charge_id     uuid NOT NULL,
  preauth_code  text NOT NULL,
  created_at    timestamptz NOT NULL DEFAULT now(),
  created_by    uuid,
  CONSTRAINT ux_charge_preauth_facility_id UNIQUE (facility_id, id),
  CONSTRAINT fk_charge_preauth_charge FOREIGN KEY (facility_id, charge_id) REFERENCES charge (facility_id, id)
);
CREATE INDEX ix_charge_preauth_charge ON charge_preauth (facility_id, charge_id);

CREATE TABLE charge_waiver (
  id           uuid PRIMARY KEY,
  facility_id  uuid NOT NULL,
  charge_id    uuid NOT NULL,
  reason       text NOT NULL,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   uuid NOT NULL,   -- the approving medical director
  CONSTRAINT ux_charge_waiver_facility_id UNIQUE (facility_id, id),
  CONSTRAINT ux_charge_waiver_charge UNIQUE (facility_id, charge_id),
  CONSTRAINT fk_charge_waiver_charge FOREIGN KEY (facility_id, charge_id) REFERENCES charge (facility_id, id)
);

CREATE TABLE charge_void (
  id           uuid PRIMARY KEY,
  facility_id  uuid NOT NULL,
  charge_id    uuid NOT NULL,
  reason       text NOT NULL,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   uuid,
  CONSTRAINT ux_charge_void_facility_id UNIQUE (facility_id, id),
  CONSTRAINT ux_charge_void_charge UNIQUE (facility_id, charge_id),
  CONSTRAINT fk_charge_void_charge FOREIGN KEY (facility_id, charge_id) REFERENCES charge (facility_id, id)
);

-- Editable until closed.
CREATE TABLE cash_up (
  id               uuid PRIMARY KEY,
  facility_id      uuid NOT NULL REFERENCES facility (id),
  cashier_user_id  uuid NOT NULL REFERENCES user_account (id),
  business_date    date NOT NULL,   -- an Africa/Lagos calendar day
  status           text NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'closed')),
  closed_at        timestamptz,
  closed_by        uuid REFERENCES user_account (id),
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   uuid,
  updated_at   timestamptz,
  updated_by   uuid,
  row_version  bigint NOT NULL DEFAULT 0,
  deleted_at   timestamptz,
  CONSTRAINT ux_cash_up_facility_id UNIQUE (facility_id, id),
  CHECK ((status = 'closed') = (closed_at IS NOT NULL))
);
CREATE UNIQUE INDEX ux_cash_up_day ON cash_up (facility_id, cashier_user_id, business_date) WHERE deleted_at IS NULL;

CREATE TABLE receipt_number_sequence (
  facility_id  uuid NOT NULL REFERENCES facility (id),
  year         integer NOT NULL,
  last_value   bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (facility_id, year)
);

CREATE TABLE payment (
  id              uuid PRIMARY KEY,
  facility_id     uuid NOT NULL,
  person_id       uuid NOT NULL,
  cash_up_id      uuid NOT NULL,
  receipt_number  text NOT NULL,   -- R/{YYYY}/{NNNNNN}
  amount_kobo     bigint NOT NULL CHECK (amount_kobo > 0),
  method          text NOT NULL CHECK (method IN ('cash', 'pos', 'bank_transfer', 'patient_credit')),
  reference       text,
  created_at      timestamptz NOT NULL DEFAULT now(),
  created_by      uuid NOT NULL,   -- the cashier
  CONSTRAINT ux_payment_facility_id UNIQUE (facility_id, id),
  CONSTRAINT ux_payment_receipt UNIQUE (facility_id, receipt_number),
  CONSTRAINT fk_payment_registration FOREIGN KEY (facility_id, person_id)
    REFERENCES patient_registration (facility_id, person_id),
  CONSTRAINT fk_payment_cash_up FOREIGN KEY (facility_id, cash_up_id) REFERENCES cash_up (facility_id, id),
  CHECK (method NOT IN ('pos', 'bank_transfer') OR reference IS NOT NULL)
);

CREATE TABLE payment_line (
  id           uuid PRIMARY KEY,
  facility_id  uuid NOT NULL,
  payment_id   uuid NOT NULL,
  charge_id    uuid NOT NULL,
  amount_kobo  bigint NOT NULL CHECK (amount_kobo > 0),
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   uuid,
  CONSTRAINT ux_payment_line_facility_id UNIQUE (facility_id, id),
  CONSTRAINT fk_payment_line_payment FOREIGN KEY (facility_id, payment_id) REFERENCES payment (facility_id, id),
  CONSTRAINT fk_payment_line_charge FOREIGN KEY (facility_id, charge_id) REFERENCES charge (facility_id, id)
);
CREATE INDEX ix_payment_line_charge ON payment_line (facility_id, charge_id);

CREATE TABLE payment_void (
  id           uuid PRIMARY KEY,
  facility_id  uuid NOT NULL,
  payment_id   uuid NOT NULL,
  reason       text NOT NULL,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   uuid NOT NULL,   -- the accounts officer
  CONSTRAINT ux_payment_void_facility_id UNIQUE (facility_id, id),
  CONSTRAINT ux_payment_void_payment UNIQUE (facility_id, payment_id),
  CONSTRAINT fk_payment_void_payment FOREIGN KEY (facility_id, payment_id) REFERENCES payment (facility_id, id)
);

CREATE TABLE refund (
  id           uuid PRIMARY KEY,
  facility_id  uuid NOT NULL,
  charge_id    uuid NOT NULL,
  payment_id   uuid NOT NULL,
  cash_up_id   uuid NOT NULL,
  amount_kobo  bigint NOT NULL CHECK (amount_kobo > 0),
  method       text NOT NULL CHECK (method IN ('cash', 'pos', 'bank_transfer')),
  reason       text NOT NULL,
  approved_by  uuid NOT NULL REFERENCES user_account (id),   -- an accounts officer
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   uuid,
  CONSTRAINT ux_refund_facility_id UNIQUE (facility_id, id),
  CONSTRAINT fk_refund_charge FOREIGN KEY (facility_id, charge_id) REFERENCES charge (facility_id, id),
  CONSTRAINT fk_refund_payment FOREIGN KEY (facility_id, payment_id) REFERENCES payment (facility_id, id),
  CONSTRAINT fk_refund_cash_up FOREIGN KEY (facility_id, cash_up_id) REFERENCES cash_up (facility_id, id)
);
CREATE INDEX ix_refund_charge ON refund (facility_id, charge_id);

-- Positive amount: credit issued. Negative amount: credit used.
CREATE TABLE patient_credit (
  id           uuid PRIMARY KEY,
  facility_id  uuid NOT NULL,
  person_id    uuid NOT NULL,
  charge_id    uuid,
  payment_id   uuid,
  amount_kobo  bigint NOT NULL CHECK (amount_kobo <> 0),
  reason       text NOT NULL,
  approved_by  uuid REFERENCES user_account (id),
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   uuid,
  CONSTRAINT ux_patient_credit_facility_id UNIQUE (facility_id, id),
  CONSTRAINT fk_patient_credit_registration FOREIGN KEY (facility_id, person_id)
    REFERENCES patient_registration (facility_id, person_id),
  CONSTRAINT fk_patient_credit_charge FOREIGN KEY (facility_id, charge_id) REFERENCES charge (facility_id, id),
  CONSTRAINT fk_patient_credit_payment FOREIGN KEY (facility_id, payment_id) REFERENCES payment (facility_id, id),
  CHECK (amount_kobo < 0 OR approved_by IS NOT NULL),   -- issuing credit needs approval
  CHECK (amount_kobo > 0 OR payment_id IS NOT NULL)     -- using credit is part of a payment
);
CREATE INDEX ix_patient_credit_person ON patient_credit (facility_id, person_id);

CREATE TABLE cash_up_adjustment (
  id                  uuid PRIMARY KEY,
  facility_id         uuid NOT NULL,
  cash_up_id          uuid NOT NULL,
  amount_kobo         bigint NOT NULL CHECK (amount_kobo <> 0),
  reason              text NOT NULL,
  related_payment_id  uuid,
  approved_by         uuid NOT NULL REFERENCES user_account (id),
  created_at          timestamptz NOT NULL DEFAULT now(),
  created_by          uuid,
  CONSTRAINT ux_cash_up_adjustment_facility_id UNIQUE (facility_id, id),
  CONSTRAINT fk_cash_up_adjustment_cash_up FOREIGN KEY (facility_id, cash_up_id) REFERENCES cash_up (facility_id, id),
  CONSTRAINT fk_cash_up_adjustment_payment FOREIGN KEY (facility_id, related_payment_id)
    REFERENCES payment (facility_id, id)
);

CREATE TABLE utilisation_record (
  id                  uuid PRIMARY KEY,
  facility_id         uuid NOT NULL,
  visit_id            uuid NOT NULL,
  coverage_id         uuid NOT NULL,
  charge_id           uuid NOT NULL,
  tariff_value_kobo   bigint NOT NULL CHECK (tariff_value_kobo >= 0),
  reimbursement_type  text NOT NULL CHECK (reimbursement_type IN ('capitated', 'institution_funded')),
  created_at          timestamptz NOT NULL DEFAULT now(),
  created_by          uuid,
  CONSTRAINT ux_utilisation_record_facility_id UNIQUE (facility_id, id),
  CONSTRAINT fk_utilisation_record_visit FOREIGN KEY (facility_id, visit_id) REFERENCES visit (facility_id, id),
  CONSTRAINT fk_utilisation_record_coverage FOREIGN KEY (facility_id, coverage_id) REFERENCES coverage (facility_id, id),
  CONSTRAINT fk_utilisation_record_charge FOREIGN KEY (facility_id, charge_id) REFERENCES charge (facility_id, id)
);
```

Two rules the database cannot check alone are the application's job: a charge is paid only once (the payment service locks the charge row with `SELECT … FOR UPDATE` before checking `charge_status`), and a closed `cash_up` accepts no new payments, refunds or adjustments. A `price_list_item` or `tariff_version` is ended by setting `effective_to` and adding a new row, never by changing its price.

## V9: Access, claims, sync and idempotency tables

The tables behind clinical access (ADR-013), claims (PRD F13, ADR-012), and the transport tables (ADR-010, ADR-011). They come last because they point at visits, lab orders and charges.

```sql
-- V9__access_claims_sync.sql

-- Platform: the policy set each audit event refers to (ADR-008). Insert-only.
CREATE TABLE policy_set (
  id                uuid PRIMARY KEY,
  version           text NOT NULL UNIQUE,   -- SHA-256 of the policy files, hex
  semantic_version  text NOT NULL,
  definition        jsonb NOT NULL,
  created_at        timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE care_relationship (
  id            uuid PRIMARY KEY,
  facility_id   uuid NOT NULL,
  person_id     uuid NOT NULL,
  user_id       uuid NOT NULL REFERENCES user_account (id),
  type          text NOT NULL CHECK (type IN ('treating_clinician', 'visit_assignee')),
  visit_id      uuid NOT NULL,
  starts_at     timestamptz NOT NULL,
  full_until    timestamptz,   -- NULL while the visit is open
  read_until    timestamptz,   -- NULL while the visit is open
  ended_reason  text CHECK (ended_reason IN ('visit_closed', 'handover', 'cancelled')),
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   uuid,
  updated_at   timestamptz,
  updated_by   uuid,
  row_version  bigint NOT NULL DEFAULT 0,
  deleted_at   timestamptz,
  CONSTRAINT ux_care_relationship_facility_id UNIQUE (facility_id, id),
  CONSTRAINT fk_care_relationship_registration FOREIGN KEY (facility_id, person_id)
    REFERENCES patient_registration (facility_id, person_id),
  CONSTRAINT fk_care_relationship_visit FOREIGN KEY (facility_id, visit_id) REFERENCES visit (facility_id, id),
  CHECK (full_until IS NULL OR read_until IS NULL OR read_until >= full_until)
);
CREATE INDEX ix_care_relationship_user ON care_relationship (facility_id, user_id, person_id);

CREATE TABLE cover_rota (
  id           uuid PRIMARY KEY,
  facility_id  uuid NOT NULL,
  clinic_id    uuid NOT NULL,
  user_id      uuid NOT NULL REFERENCES user_account (id),
  starts_on    date NOT NULL,
  ends_on      date NOT NULL CHECK (ends_on >= starts_on),
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   uuid,
  updated_at   timestamptz,
  updated_by   uuid,
  row_version  bigint NOT NULL DEFAULT 0,
  deleted_at   timestamptz,
  CONSTRAINT ux_cover_rota_facility_id UNIQUE (facility_id, id),
  CONSTRAINT fk_cover_rota_clinic FOREIGN KEY (facility_id, clinic_id) REFERENCES clinic (facility_id, id)
);
CREATE INDEX ix_cover_rota_user ON cover_rota (facility_id, user_id, starts_on, ends_on);

CREATE TABLE break_glass_grant (
  id                      uuid PRIMARY KEY,
  facility_id             uuid NOT NULL,
  person_id               uuid NOT NULL,
  user_id                 uuid NOT NULL REFERENCES user_account (id),
  reason_code             text NOT NULL CHECK (reason_code IN ('emergency', 'patient_unable_to_consent', 'other')),
  reason_text             text,
  justification           text NOT NULL,   -- why access is needed now
  overridden_restriction  text NOT NULL,   -- for example 'care_relationship'
  starts_at               timestamptz NOT NULL,
  expires_at              timestamptz NOT NULL CHECK (expires_at > starts_at),
  reviewed_by             uuid REFERENCES user_account (id),
  reviewed_at             timestamptz,
  review_outcome          text CHECK (review_outcome IN ('appropriate', 'inappropriate')),
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   uuid,
  updated_at   timestamptz,
  updated_by   uuid,
  row_version  bigint NOT NULL DEFAULT 0,
  deleted_at   timestamptz,
  CONSTRAINT ux_break_glass_grant_facility_id UNIQUE (facility_id, id),
  CONSTRAINT fk_break_glass_grant_registration FOREIGN KEY (facility_id, person_id)
    REFERENCES patient_registration (facility_id, person_id),
  CHECK (reason_code <> 'other' OR reason_text IS NOT NULL),
  CHECK ((reviewed_at IS NULL) = (review_outcome IS NULL))
);
CREATE INDEX ix_break_glass_grant_active ON break_glass_grant (facility_id, user_id, person_id, expires_at);

-- Insert-only.
CREATE TABLE risk_event (
  id           uuid PRIMARY KEY,
  facility_id  uuid NOT NULL REFERENCES facility (id),
  user_id      uuid NOT NULL REFERENCES user_account (id),
  signal       text NOT NULL,          -- for example 'many_patients_opened'
  score        integer NOT NULL CHECK (score >= 0),
  action       text NOT NULL CHECK (action IN ('none', 'step_up', 'deny')),
  created_at   timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ux_risk_event_facility_id UNIQUE (facility_id, id)
);

CREATE TABLE export_profile (
  id              uuid PRIMARY KEY,
  facility_id     uuid NOT NULL REFERENCES facility (id),
  payer_id        uuid NOT NULL REFERENCES payer (id),
  name            text NOT NULL,
  format          text NOT NULL DEFAULT 'csv' CHECK (format IN ('csv')),
  column_mapping  jsonb NOT NULL,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   uuid,
  updated_at   timestamptz,
  updated_by   uuid,
  row_version  bigint NOT NULL DEFAULT 0,
  deleted_at   timestamptz,
  CONSTRAINT ux_export_profile_facility_id UNIQUE (facility_id, id)
);

-- Editable: its status moves through the claim workflow (PRD F13).
CREATE TABLE claim (
  id                  uuid PRIMARY KEY,
  facility_id         uuid NOT NULL,
  visit_id            uuid NOT NULL,
  coverage_id         uuid NOT NULL,
  payer_id            uuid NOT NULL REFERENCES payer (id),
  status              text NOT NULL DEFAULT 'draft' CHECK (status IN (
                        'draft', 'ready', 'exported', 'submitted', 'acknowledged', 'queried',
                        'approved', 'partly_approved', 'rejected', 'paid')),
  exported_at         timestamptz,
  submitted_at        timestamptz,
  submitted_by        uuid REFERENCES user_account (id),
  submission_channel  text CHECK (submission_channel IN ('portal', 'email', 'paper', 'api')),
  payer_reference     text,
  acknowledged_at     timestamptz,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   uuid,
  updated_at   timestamptz,
  updated_by   uuid,
  row_version  bigint NOT NULL DEFAULT 0,
  deleted_at   timestamptz,
  CONSTRAINT ux_claim_facility_id UNIQUE (facility_id, id),
  CONSTRAINT fk_claim_visit FOREIGN KEY (facility_id, visit_id) REFERENCES visit (facility_id, id),
  CONSTRAINT fk_claim_coverage FOREIGN KEY (facility_id, coverage_id) REFERENCES coverage (facility_id, id),
  CHECK ((submitted_at IS NULL) = (submission_channel IS NULL))
);
CREATE UNIQUE INDEX ux_claim_visit_coverage ON claim (facility_id, visit_id, coverage_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_claim_status ON claim (facility_id, payer_id, status);

-- Insert-only from here, except the transport tables at the end.
CREATE TABLE claim_line (
  id                 uuid PRIMARY KEY,
  facility_id        uuid NOT NULL,
  claim_id           uuid NOT NULL,
  charge_id          uuid NOT NULL,
  tariff_version_id  uuid NOT NULL,
  claimed_kobo       bigint NOT NULL CHECK (claimed_kobo >= 0),
  created_at         timestamptz NOT NULL DEFAULT now(),
  created_by         uuid,
  CONSTRAINT ux_claim_line_facility_id UNIQUE (facility_id, id),
  CONSTRAINT ux_claim_line_charge UNIQUE (facility_id, charge_id),
  CONSTRAINT fk_claim_line_claim FOREIGN KEY (facility_id, claim_id) REFERENCES claim (facility_id, id),
  CONSTRAINT fk_claim_line_charge FOREIGN KEY (facility_id, charge_id) REFERENCES charge (facility_id, id),
  CONSTRAINT fk_claim_line_tariff FOREIGN KEY (facility_id, tariff_version_id)
    REFERENCES tariff_version (facility_id, id)
);

CREATE TABLE claim_response (
  id              uuid PRIMARY KEY,
  facility_id     uuid NOT NULL,
  claim_line_id   uuid NOT NULL,
  approved_kobo   bigint NOT NULL CHECK (approved_kobo >= 0),
  rejection_code  text REFERENCES rejection_reason (code),
  note            text,
  received_on     date NOT NULL,
  created_at      timestamptz NOT NULL DEFAULT now(),
  created_by      uuid,
  CONSTRAINT ux_claim_response_facility_id UNIQUE (facility_id, id),
  CONSTRAINT fk_claim_response_line FOREIGN KEY (facility_id, claim_line_id) REFERENCES claim_line (facility_id, id)
);
CREATE INDEX ix_claim_response_line ON claim_response (facility_id, claim_line_id);

CREATE TABLE claim_payment (
  id           uuid PRIMARY KEY,
  facility_id  uuid NOT NULL,
  claim_id     uuid NOT NULL,
  amount_kobo  bigint NOT NULL CHECK (amount_kobo > 0),
  paid_on      date NOT NULL,
  reference    text,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   uuid,
  CONSTRAINT ux_claim_payment_facility_id UNIQUE (facility_id, id),
  CONSTRAINT fk_claim_payment_claim FOREIGN KEY (facility_id, claim_id) REFERENCES claim (facility_id, id)
);

CREATE TABLE claim_writeoff (
  id             uuid PRIMARY KEY,
  facility_id    uuid NOT NULL,
  claim_line_id  uuid NOT NULL,
  amount_kobo    bigint NOT NULL CHECK (amount_kobo > 0),
  reason         text NOT NULL,
  approved_by    uuid NOT NULL REFERENCES user_account (id),   -- an accounts officer
  created_at     timestamptz NOT NULL DEFAULT now(),
  created_by     uuid,
  CONSTRAINT ux_claim_writeoff_facility_id UNIQUE (facility_id, id),
  CONSTRAINT fk_claim_writeoff_line FOREIGN KEY (facility_id, claim_line_id) REFERENCES claim_line (facility_id, id)
);

CREATE TABLE claim_export (
  id                 uuid PRIMARY KEY,
  facility_id        uuid NOT NULL,
  payer_id           uuid NOT NULL REFERENCES payer (id),
  export_profile_id  uuid NOT NULL,
  file_name          text NOT NULL,
  file_sha256        bytea NOT NULL CHECK (length(file_sha256) = 32),
  row_count          integer NOT NULL CHECK (row_count > 0),
  created_at         timestamptz NOT NULL DEFAULT now(),
  created_by         uuid,
  CONSTRAINT ux_claim_export_facility_id UNIQUE (facility_id, id),
  CONSTRAINT fk_claim_export_profile FOREIGN KEY (facility_id, export_profile_id)
    REFERENCES export_profile (facility_id, id)
);

CREATE TABLE claim_export_item (
  claim_export_id  uuid NOT NULL,
  claim_id         uuid NOT NULL,
  facility_id      uuid NOT NULL,
  PRIMARY KEY (claim_export_id, claim_id),
  CONSTRAINT fk_claim_export_item_export FOREIGN KEY (facility_id, claim_export_id)
    REFERENCES claim_export (facility_id, id),
  CONSTRAINT fk_claim_export_item_claim FOREIGN KEY (facility_id, claim_id) REFERENCES claim (facility_id, id)
);

-- Transport (ADR-011): written in the business transaction; the sender deletes a row once delivered.
-- The writer stays switched off until Phase 2.
CREATE TABLE outbox_entry (
  id               uuid PRIMARY KEY,
  facility_id      uuid NOT NULL REFERENCES facility (id),
  aggregate_type   text NOT NULL,
  aggregate_id     uuid NOT NULL,
  event_type       text NOT NULL,
  payload          jsonb NOT NULL,
  idempotency_key  uuid NOT NULL UNIQUE,
  attempts         integer NOT NULL DEFAULT 0,
  next_attempt_at  timestamptz NOT NULL DEFAULT now(),
  created_at       timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_outbox_entry_due ON outbox_entry (facility_id, next_attempt_at);

-- Transport (ADR-010).
CREATE TABLE idempotency_record (
  id               uuid PRIMARY KEY,
  facility_id      uuid NOT NULL REFERENCES facility (id),
  user_id          uuid NOT NULL REFERENCES user_account (id),
  idempotency_key  uuid NOT NULL,
  operation        text NOT NULL,
  request_hash     bytea NOT NULL CHECK (length(request_hash) = 32),
  response_status  integer,
  response_body    jsonb,
  expires_at       timestamptz NOT NULL,
  created_at       timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ux_idempotency_record_key UNIQUE (facility_id, user_id, idempotency_key)
);
CREATE INDEX ix_idempotency_record_expiry ON idempotency_record (expires_at);
```

`claim_line` carries `UNIQUE (facility_id, charge_id)`, so a charge can be claimed once. `response_status` and `response_body` are NULL only inside the transaction that inserts the row; once committed, both are set (ADR-010).

## Security statements in every migration

These statements are not a migration of their own. Each migration ends with the ones for the tables it creates, so a table is protected from the moment it exists; one security migration at the end would leave tables open for several sprints, because the schema is built ticket by ticket. The transaction hook sets two values at the start of every transaction: `app.facility_id` (the active facility) and `app.user_id` (the signed-in user). The second exists for one reason: at sign-in, before a facility is chosen, a user must be able to list their own memberships.

```sql
-- Security statements. These are not a migration of their own: every migration ends
-- with the ones for the tables it creates, so a table is protected from the moment it exists.

-- 1. Facility isolation, for every table with facility_id except the two membership
--    tables (clinic shown; the same three statements for each table).
ALTER TABLE clinic ENABLE ROW LEVEL SECURITY;
ALTER TABLE clinic FORCE ROW LEVEL SECURITY;
CREATE POLICY p_facility ON clinic
  USING (facility_id = NULLIF(current_setting('app.facility_id', true), '')::uuid)
  WITH CHECK (facility_id = NULLIF(current_setting('app.facility_id', true), '')::uuid);

-- 2. End of V3. Memberships: visible in the active facility, or to their own user (for sign-in).
ALTER TABLE facility_membership ENABLE ROW LEVEL SECURITY;
ALTER TABLE facility_membership FORCE ROW LEVEL SECURITY;
CREATE POLICY p_facility_membership ON facility_membership
  USING (facility_id = NULLIF(current_setting('app.facility_id', true), '')::uuid
      OR user_account_id = NULLIF(current_setting('app.user_id', true), '')::uuid)
  WITH CHECK (facility_id = NULLIF(current_setting('app.facility_id', true), '')::uuid);

ALTER TABLE role_assignment ENABLE ROW LEVEL SECURITY;
ALTER TABLE role_assignment FORCE ROW LEVEL SECURITY;
CREATE POLICY p_role_assignment ON role_assignment
  USING (facility_id = NULLIF(current_setting('app.facility_id', true), '')::uuid
      OR membership_id IN (
           SELECT m.id FROM facility_membership m
           WHERE m.user_account_id = NULLIF(current_setting('app.user_id', true), '')::uuid))
  WITH CHECK (facility_id = NULLIF(current_setting('app.facility_id', true), '')::uuid);

-- 3. Grants. V1's default privileges already give ereach_app SELECT, INSERT and UPDATE on
--    every table Flyway creates, and nothing more. The application never hard-deletes,
--    except transport and session rows: V1_1 grants DELETE on spring_session,
--    spring_session_attributes and event_publication, and the migration that creates
--    outbox_entry and idempotency_record grants it on those two.
GRANT DELETE ON outbox_entry, idempotency_record TO ereach_app;

-- 4. Insert-only tables: each migration revokes UPDATE on the ones it creates.
REVOKE UPDATE ON
  audit_event, audit_seal, audit_seal_member, audit_seal_shipment, person, consent_record,
  encounter_addendum, lab_result, lab_result_verification, lab_result_view, result_acknowledgement,
  stock_movement, dispense, charge, charge_preauth, charge_waiver, charge_void, payment, payment_line,
  payment_void, refund, patient_credit, cash_up_adjustment, utilisation_record, claim_line,
  claim_response, claim_payment, claim_writeoff, claim_export, claim_export_item, risk_event, policy_set
FROM ereach_app;

-- 5. Reference tables: read only for the application (R__ migrations load them as ereach_owner);
--    each migration revokes on the ones it creates.
REVOKE INSERT, UPDATE ON
  state, lga, icd10_code, drug, drug_brand, lab_test, lab_analyte, lab_reference_range, frequency,
  consent_type, rejection_reason, payer, payer_plan, benefit_rule
FROM ereach_app;

-- 6. Views. security_invoker makes the caller's row-level security apply inside the view.
-- End of V8:
CREATE VIEW charge_status WITH (security_invoker = true) AS
SELECT
  c.id AS charge_id,
  c.facility_id,
  c.visit_id,
  c.amount_kobo,
  c.patient_kobo,
  c.payer_kobo,
  COALESCE(paid.amount_kobo, 0) AS paid_kobo,
  COALESCE(refunded.amount_kobo, 0) AS refunded_kobo,
  CASE
    WHEN v.id IS NOT NULL THEN 'void'
    WHEN w.id IS NOT NULL THEN 'waived'
    WHEN c.patient_kobo = 0 THEN 'covered'
    WHEN COALESCE(paid.amount_kobo, 0) >= c.patient_kobo THEN 'paid'
    ELSE 'unpaid'
  END AS status
FROM charge c
LEFT JOIN charge_void v ON v.facility_id = c.facility_id AND v.charge_id = c.id
LEFT JOIN charge_waiver w ON w.facility_id = c.facility_id AND w.charge_id = c.id
LEFT JOIN LATERAL (
  SELECT SUM(pl.amount_kobo) AS amount_kobo
  FROM payment_line pl
  WHERE pl.facility_id = c.facility_id
    AND pl.charge_id = c.id
    AND NOT EXISTS (SELECT 1 FROM payment_void pv
                    WHERE pv.facility_id = pl.facility_id AND pv.payment_id = pl.payment_id)
) paid ON true
LEFT JOIN LATERAL (
  SELECT SUM(r.amount_kobo) AS amount_kobo
  FROM refund r
  WHERE r.facility_id = c.facility_id AND r.charge_id = c.id
) refunded ON true;
GRANT SELECT ON charge_status TO ereach_app;

-- End of V5: the newest decision per person, consent type, grantee and scope.
CREATE VIEW consent_current WITH (security_invoker = true) AS
SELECT DISTINCT ON (facility_id, person_id, consent_type_code, grantee_facility_id, scope)
  id, facility_id, person_id, consent_type_code, grantee_facility_id, purpose, scope,
  status, decided_at
FROM consent_record
ORDER BY facility_id, person_id, consent_type_code, grantee_facility_id, scope, decided_at DESC;
GRANT SELECT ON consent_current TO ereach_app;
```

The `DELETE` grant is never given on other tables, so a code path that tries a hard delete fails loudly. The schema test from ADR-003 fails the build if any table with `facility_id` lacks enabled and forced row-level security, so a migration that forgets these statements cannot be merged.
