-- 1. User Accounts.
CREATE TABLE user_account (
    id                      UUID PRIMARY KEY,
    kind                    TEXT NOT NULL DEFAULT 'person' CHECK (kind IN ('person', 'system')),
    username                TEXT NOT NULL CHECK (username ~ '^[A-Za-z0-9._-]{3,40}$'), -- stored as the user typed it (D-021)
    full_name               TEXT NOT NULL CHECK (btrim(full_name) <> ''),
    email                   TEXT CHECK (email = lower(email) AND email ~ '^[^@\s]+@[^@\s]+\.[^@\s]+$'),
    phone                   TEXT CHECK (phone ~ '^\+[1-9][0-9]{6,14}$'),
    password_hash           TEXT,
    must_change_password    BOOLEAN NOT NULL DEFAULT false, -- true only after an admin forces a reset; nobody gets a temporary password (D-023)
    pin_hash                TEXT,
    mfa_required_by         TIMESTAMPTZ,-- end of a clinician's 30-day grace period, they must have a confirmed MFA method by then
    status                  TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'suspended')),
    is_platform_operator    BOOLEAN NOT NULL DEFAULT false,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by              UUID NOT NULL DEFAULT NULLIF(current_setting('app.user_id', true), '')::UUID,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by              UUID NOT NULL DEFAULT NULLIF(current_setting('app.user_id', true), '')::UUID,
    row_version             BIGINT NOT NULL DEFAULT 0,
    deleted_at              TIMESTAMPTZ,

    -- A person (human) always has a password, but a system actor don't have one.
    CONSTRAINT ck_user_account_password CHECK ((kind = 'person') = (password_hash IS NOT NULL)),

    -- A person always has an email address (D-022); a system actor never has one (next check).
    CONSTRAINT ck_user_account_person_email CHECK (kind <> 'person' OR email IS NOT NULL),

    -- A system actor never has a PIN, an email or an MFA deadline, and is never a platform operator.
    CONSTRAINT ck_user_account_system_secrets
        CHECK (kind = 'person' OR (pin_hash IS NULL AND email IS NULL AND mfa_required_by IS NULL AND is_platform_operator = false))

    -- If mfa_required_by is not null and its value is lesser than now
);

-- Unique ignoring case (D-021): Amina.Bello and amina.bello can never be two different accounts.
CREATE UNIQUE INDEX ux_user_account_username ON user_account (lower(username)) WHERE deleted_at IS NULL;
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
