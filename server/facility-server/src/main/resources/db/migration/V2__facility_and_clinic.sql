-- 1. Helper function to handle updated_at triggers automatically
CREATE OR REPLACE FUNCTION update_modified_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
RETURN NEW;
END;
$$ language 'plpgsql';


-- 2. State Reference Table
CREATE TABLE state (
                       code VARCHAR(10) NOT NULL PRIMARY KEY,
                       name VARCHAR(100) NOT NULL,
                       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TRIGGER update_state_modtime
    BEFORE UPDATE ON state
    FOR EACH ROW
    EXECUTE FUNCTION update_modified_column();


-- 3. LGA Reference Table (with compound unique key for state-scoping)
CREATE TABLE lga (
    code VARCHAR(20) NOT NULL,
    state_code VARCHAR(10) NOT NULL REFERENCES state(code) ON UPDATE CASCADE ON DELETE RESTRICT,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT lga_pkey PRIMARY KEY (code, state_code)
);

CREATE TRIGGER update_lga_modtime
    BEFORE UPDATE ON lga
    FOR EACH ROW
    EXECUTE FUNCTION update_modified_column();


-- 4. Facility Table (updated to reference state and lga using codes)
CREATE TABLE facility (
    id UUID NOT NULL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL CHECK (type IN ('primary', 'secondary', 'tertiary')),
    state_code VARCHAR(10) NOT NULL,
    lga_code VARCHAR(20) NOT NULL,
    address VARCHAR(255),
    hospital_number_prefix VARCHAR(50) NOT NULL,
    phone_number VARCHAR(20),
    nhfr_code VARCHAR(50),
    state_reg_number VARCHAR(50),
    state_reg_number_issuer VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- Foreign key constraint ensuring the pair (lga_code, state_code) exists in the lga table
    FOREIGN KEY (lga_code, state_code) REFERENCES lga(code, state_code)
);

-- Trigger to auto-update updated_at on facility
CREATE TRIGGER update_facility_modtime
    BEFORE UPDATE ON facility
    For EACH ROW
    EXECUTE FUNCTION update_modified_column();

-- Row Level Security for facility
-- ALTER TABLE facility ENABLE ROW LEVEL SECURITY;
-- CREATE POLICY facility_policy ON facility
--     USING (id = NULLIF(current_setting('app.facility_id', true), '')::uuid);
-- ALTER TABLE facility FORCE ROW LEVEL SECURITY;


-- 5. Facility Settings Table
CREATE TABLE facility_setting (
    id UUID NOT NULL PRIMARY KEY,
    facility_id UUID NOT NULL REFERENCES facility(id),
    key_ VARCHAR(50) NOT NULL CHECK (key_ IN (
        'gate.consultation_requires_payment', 'gate.lab_requires_payment', 'gate.pharmacy_requires_payment',
        'lab.verification_required', 'session.idle_lock_minutes', 'session.max_hours', 'receipt.gapless'
    )),
    value_ JSONB NOT NULL,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    row_version INT DEFAULT 1
);

-- Trigger to auto-update updated_at on facility_setting
CREATE TRIGGER update_facility_setting_modtime
    BEFORE UPDATE ON facility_setting
    FOR EACH ROW
    EXECUTE FUNCTION update_modified_column();

ALTER TABLE facility_setting ENABLE ROW LEVEL SECURITY;
CREATE POLICY facility_setting_policy ON facility_setting
    USING (facility_id = NULLIF(current_setting('app.facility_id', true), '')::uuid)
    WITH CHECK (facility_id = NULLIF(current_setting('app.facility_id', true), '')::uuid);
ALTER TABLE facility_setting FORCE ROW LEVEL SECURITY;


-- 6. Clinic Table
CREATE TABLE clinic (
    id UUID NOT NULL PRIMARY KEY,
    facility_id UUID NOT NULL REFERENCES facility(id),
    code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(255),
    phone_number VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX clinic_unique_index ON clinic(facility_id, code);

ALTER TABLE clinic ENABLE ROW LEVEL SECURITY;
CREATE POLICY clinic_policy ON clinic
    USING (facility_id = NULLIF(current_setting('app.facility_id', true), '')::uuid)
    WITH CHECK (facility_id = NULLIF(current_setting('app.facility_id', true), '')::uuid);
ALTER TABLE clinic FORCE ROW LEVEL SECURITY;