-- 1. Helper function to handle updated_at triggers automatically
-- CREATE OR REPLACE FUNCTION update_modified_column()
-- RETURNS TRIGGER AS $$
-- BEGIN
--     NEW.updated_at = CURRENT_TIMESTAMP;
-- RETURN NEW;
-- END;
-- $$ language 'plpgsql';

CREATE OR REPLACE FUNCTION set_audit_columns()
RETURNS TRIGGER AS $$
BEGIN
  NEW.created_at := OLD.created_at;
  NEW.created_by := OLD.created_by;
  NEW.updated_at := now();
  NEW.updated_by := NULLIF(current_setting('app.user_id', true), '')::uuid;
RETURN NEW;
END
$$ LANGUAGE plpgsql;

-- 2. State Reference Table
CREATE TABLE state (
    code VARCHAR(10) NOT NULL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ
);

CREATE TRIGGER update_state_modtime
    BEFORE UPDATE ON state
    FOR EACH ROW
    EXECUTE FUNCTION set_audit_columns();

REVOKE INSERT, UPDATE ON state FROM ereach_app;

-- 3. LGA Reference Table (with compound unique key for state-scoping)
CREATE TABLE lga (
    code TEXT PRIMARY KEY ,
    state_code VARCHAR(10) NOT NULL REFERENCES state(code) ON UPDATE CASCADE ON DELETE RESTRICT,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ,
    CONSTRAINT ux_lga_state_code UNIQUE (state_code, code)
);

CREATE TRIGGER update_lga_modtime
    BEFORE UPDATE ON lga
    FOR EACH ROW
    EXECUTE FUNCTION set_audit_columns();

REVOKE INSERT, UPDATE ON lga FROM ereach_app;

/*

 */

-- 4. Facility Table (updated to reference state and lga using codes)
CREATE TABLE facility (
    id UUID NOT NULL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL CHECK (type IN ('phc', 'clinic', 'secondary', 'tertiary')),
    state_code VARCHAR(10) NOT NULL,
    lga_code VARCHAR(20) NOT NULL,
    address VARCHAR(255),
    status text NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'deactivated')),
    phone_number VARCHAR(20),
    nhfr_code text CHECK (btrim(nhfr_code) <> ''),
    state_registration_number text CHECK (btrim(state_registration_number) <> ''),
    state_registration_agency text CHECK (btrim(state_registration_agency) <> ''),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  UUID        NOT NULL DEFAULT NULLIF(current_setting('app.user_id', true), '')::uuid,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by  UUID        NOT NULL DEFAULT NULLIF(current_setting('app.user_id', true), '')::uuid,
    deleted_at TIMESTAMPTZ,
    row_version BIGINT DEFAULT 0,
    hospital_number_prefix VARCHAR(50) NOT NULL CHECK (hospital_number_prefix ~ '^[A-Z]{2,6}$'),

    CHECK ((state_registration_number IS NULL) = (state_registration_agency IS NULL)),

    -- Foreign key constraint ensuring the pair (state_code, lga_code) exists in the lga table
    CONSTRAINT fk_facility_lga FOREIGN KEY (state_code, lga_code) REFERENCES lga (state_code, code)
);
CREATE UNIQUE INDEX ux_facility_prefix ON facility (hospital_number_prefix);
CREATE UNIQUE INDEX ux_facility_nhfr_code ON facility (nhfr_code);

-- Trigger to auto-update updated_at on facility
CREATE TRIGGER update_facility_modtime
    BEFORE UPDATE ON facility
    For EACH ROW
    EXECUTE FUNCTION set_audit_columns();

-- Row Level Security for facility
-- ALTER TABLE facility ENABLE ROW LEVEL SECURITY;
-- CREATE POLICY facility_policy ON facility
--     USING (id = NULLIF(current_setting('app.facility_id', true), '')::uuid);
-- ALTER TABLE facility FORCE ROW LEVEL SECURITY;


-- 5. Facility Settings Table
CREATE TABLE facility_setting (
    id UUID NOT NULL PRIMARY KEY,
    facility_id UUID NOT NULL REFERENCES facility(id),
    key VARCHAR(50) NOT NULL CHECK (key IN (
        'gate.consultation_requires_payment', 'gate.lab_requires_payment', 'gate.pharmacy_requires_payment',
        'lab.verification_required', 'session.idle_lock_minutes', 'session.max_hours', 'receipt.gapless'
    )),
    value JSONB NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  UUID        NOT NULL DEFAULT NULLIF(current_setting('app.user_id', true), '')::uuid,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by  UUID        NOT NULL DEFAULT NULLIF(current_setting('app.user_id', true), '')::uuid,
    deleted_at TIMESTAMPTZ,
    row_version BIGINT DEFAULT 0,

    CONSTRAINT ux_facility_setting_facility_id UNIQUE (facility_id, id)
);
CREATE UNIQUE INDEX ux_facility_setting_key
    ON facility_setting (facility_id, key) WHERE deleted_at IS NULL;

-- Trigger to auto-update updated_at on facility_setting
CREATE TRIGGER update_facility_setting_modtime
    BEFORE UPDATE ON facility_setting
    FOR EACH ROW
    EXECUTE FUNCTION set_audit_columns();

ALTER TABLE facility_setting ENABLE ROW LEVEL SECURITY;
CREATE POLICY facility_setting_policy ON facility_setting
    USING (facility_id = NULLIF(current_setting('app.facility_id', true), '')::uuid)
    WITH CHECK (facility_id = NULLIF(current_setting('app.facility_id', true), '')::uuid);
ALTER TABLE facility_setting FORCE ROW LEVEL SECURITY;


-- 6. Clinic Table
CREATE TABLE clinic (
    id UUID NOT NULL PRIMARY KEY,
    facility_id UUID NOT NULL REFERENCES facility(id),
    code VARCHAR(50) NOT NULL CHECK (code ~ '^[A-Z0-9_]{2,20}$'),
    status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'inactive')),
    name VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  UUID        NOT NULL DEFAULT NULLIF(current_setting('app.user_id', true), '')::uuid,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by  UUID        NOT NULL DEFAULT NULLIF(current_setting('app.user_id', true), '')::uuid,
    row_version BIGINT DEFAULT 0,
    deleted_at TIMESTAMPTZ,

    CONSTRAINT ux_clinic_facility_id UNIQUE (facility_id, id)
);

CREATE UNIQUE INDEX ux_clinic_code
    ON clinic (facility_id, code) WHERE deleted_at IS NULL;

ALTER TABLE clinic ENABLE ROW LEVEL SECURITY;
CREATE POLICY clinic_policy ON clinic
    USING (facility_id = NULLIF(current_setting('app.facility_id', true), '')::uuid)
    WITH CHECK (facility_id = NULLIF(current_setting('app.facility_id', true), '')::uuid);
ALTER TABLE clinic FORCE ROW LEVEL SECURITY;