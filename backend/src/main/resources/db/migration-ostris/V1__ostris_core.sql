CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE SCHEMA IF NOT EXISTS ostris;

CREATE TABLE ostris.community (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES idax_core.tenant(tenant_id),
    name VARCHAR(160) NOT NULL,
    next_sequence BIGINT NOT NULL DEFAULT 1 CHECK (next_sequence > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, id)
);

CREATE TABLE ostris.unit_of_account (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES idax_core.tenant(tenant_id),
    community_id UUID NOT NULL REFERENCES ostris.community(id),
    code VARCHAR(16) NOT NULL CHECK (code ~ '^[A-Z][A-Z0-9_-]{0,15}$'),
    scale SMALLINT NOT NULL CHECK (scale BETWEEN 0 AND 18),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (community_id, code),
    UNIQUE (tenant_id, id)
);

CREATE TABLE ostris.risk_subject (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES idax_core.tenant(tenant_id),
    community_id UUID NOT NULL REFERENCES ostris.community(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id)
);

CREATE TABLE ostris.participant (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES idax_core.tenant(tenant_id),
    community_id UUID NOT NULL REFERENCES ostris.community(id),
    risk_subject_id UUID REFERENCES ostris.risk_subject(id),
    display_name VARCHAR(160) NOT NULL,
    profile_status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE' CHECK (profile_status IN ('ACTIVE','RETIRED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id)
);

CREATE TABLE ostris.identity_assurance_claim (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES idax_core.tenant(tenant_id),
    community_id UUID NOT NULL REFERENCES ostris.community(id),
    risk_subject_id UUID NOT NULL REFERENCES ostris.risk_subject(id),
    provider_id VARCHAR(100) NOT NULL,
    opaque_subject_ref VARCHAR(255) NOT NULL,
    claim_profile VARCHAR(100) NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ,
    status VARCHAR(24) NOT NULL CHECK (status IN ('ACTIVE','EXPIRED','REVOKED','SUPERSEDED')),
    revocation_sequence BIGINT,
    UNIQUE (community_id, provider_id, opaque_subject_ref),
    UNIQUE (tenant_id, id)
);

CREATE TABLE ostris.account (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES idax_core.tenant(tenant_id),
    community_id UUID NOT NULL REFERENCES ostris.community(id),
    unit_id UUID NOT NULL REFERENCES ostris.unit_of_account(id),
    participant_id UUID REFERENCES ostris.participant(id),
    account_type VARCHAR(32) NOT NULL CHECK (account_type IN ('INDIVIDUAL','ORGANIZATION','COMMUNITY_LOSS','COMMUNITY_PENALTY')),
    name VARCHAR(160) NOT NULL,
    credit_floor NUMERIC(78,0) NOT NULL DEFAULT 0 CHECK (credit_floor <= 0),
    balance_projection NUMERIC(78,0) NOT NULL DEFAULT 0,
    risk_state VARCHAR(24) NOT NULL DEFAULT 'ACTIVE' CHECK (risk_state IN ('ACTIVE','DORMANT','ZOMBIE','UNDER_REVIEW','RESTRICTED','DEFAULTED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, id)
);
CREATE UNIQUE INDEX uq_ostris_special_account ON ostris.account(community_id, unit_id, account_type)
    WHERE account_type IN ('COMMUNITY_LOSS','COMMUNITY_PENALTY');

CREATE TABLE ostris.credential (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES idax_core.tenant(tenant_id),
    community_id UUID NOT NULL REFERENCES ostris.community(id),
    public_key_base64url VARCHAR(64) NOT NULL,
    algorithm VARCHAR(16) NOT NULL CHECK (algorithm = 'Ed25519'),
    activation_sequence BIGINT NOT NULL,
    revocation_sequence BIGINT,
    UNIQUE (community_id, public_key_base64url),
    UNIQUE (tenant_id, id)
);

CREATE TABLE ostris.protocol_controller (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES idax_core.tenant(tenant_id),
    community_id UUID NOT NULL REFERENCES ostris.community(id),
    activation_sequence BIGINT NOT NULL,
    revocation_sequence BIGINT,
    UNIQUE (tenant_id,id)
);

CREATE TABLE ostris.controller_credential_binding (
    credential_id UUID PRIMARY KEY REFERENCES ostris.credential(id),
    tenant_id UUID NOT NULL REFERENCES idax_core.tenant(tenant_id),
    community_id UUID NOT NULL REFERENCES ostris.community(id),
    controller_id UUID NOT NULL REFERENCES ostris.protocol_controller(id),
    public_key_base64url VARCHAR(64) NOT NULL,
    activation_sequence BIGINT NOT NULL,
    revocation_sequence BIGINT,
    UNIQUE(community_id,public_key_base64url),
    UNIQUE(tenant_id,credential_id),
    UNIQUE(tenant_id,controller_id,credential_id)
);

CREATE TABLE ostris.account_control_policy (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES idax_core.tenant(tenant_id),
    community_id UUID NOT NULL REFERENCES ostris.community(id),
    account_id UUID NOT NULL REFERENCES ostris.account(id),
    policy_version INTEGER NOT NULL CHECK (policy_version > 0),
    threshold SMALLINT NOT NULL CHECK (threshold > 0),
    activation_sequence BIGINT NOT NULL,
    superseded_sequence BIGINT,
    UNIQUE (account_id, policy_version),
    UNIQUE (tenant_id, id)
);

CREATE TABLE ostris.account_controller (
    tenant_id UUID NOT NULL REFERENCES idax_core.tenant(tenant_id),
    control_policy_id UUID NOT NULL REFERENCES ostris.account_control_policy(id),
    controller_id UUID NOT NULL REFERENCES ostris.protocol_controller(id),
    PRIMARY KEY (control_policy_id, controller_id)
);

CREATE TABLE ostris.policy_version (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES idax_core.tenant(tenant_id),
    community_id UUID NOT NULL REFERENCES ostris.community(id),
    policy_type VARCHAR(32) NOT NULL CHECK (policy_type IN ('IDENTITY','CREDIT','RISK','DEFAULT','SANCTION','REHABILITATION')),
    policy_version INTEGER NOT NULL CHECK (policy_version > 0),
    activation_sequence BIGINT NOT NULL,
    superseded_sequence BIGINT,
    config_json JSONB NOT NULL,
    config_digest VARCHAR(64) NOT NULL CHECK (config_digest ~ '^[0-9a-f]{64}$'),
    UNIQUE (community_id, policy_type, policy_version),
    UNIQUE (tenant_id, id)
);

CREATE TABLE ostris.finding (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES idax_core.tenant(tenant_id),
    community_id UUID NOT NULL REFERENCES ostris.community(id),
    case_id UUID NOT NULL,
    status VARCHAR(24) NOT NULL CHECK (status IN ('UNDER_REVIEW','ISSUED','APPEALED','FINAL','OVERTURNED')),
    rule_refs_json JSONB NOT NULL DEFAULT '[]',
    evidence_refs_json JSONB NOT NULL DEFAULT '[]',
    decision_authority VARCHAR(160),
    reason_codes_json JSONB NOT NULL DEFAULT '[]',
    reasoning TEXT,
    issued_at TIMESTAMPTZ,
    appeal_deadline TIMESTAMPTZ,
    issued_sequence BIGINT,
    finality_sequence BIGINT,
    UNIQUE (tenant_id, id)
);

CREATE TABLE ostris.appeal (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES idax_core.tenant(tenant_id),
    community_id UUID NOT NULL REFERENCES ostris.community(id),
    finding_id UUID NOT NULL REFERENCES ostris.finding(id),
    status VARCHAR(24) NOT NULL CHECK (status IN ('OPEN','UPHELD','OVERTURNED')),
    opened_sequence BIGINT NOT NULL,
    resolved_sequence BIGINT,
    reasoning TEXT,
    UNIQUE (tenant_id, id)
);

CREATE TABLE ostris.identity_continuity_decision (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES idax_core.tenant(tenant_id),
    community_id UUID NOT NULL REFERENCES ostris.community(id),
    risk_subject_id UUID NOT NULL REFERENCES ostris.risk_subject(id),
    participant_id UUID NOT NULL REFERENCES ostris.participant(id),
    status VARCHAR(24) NOT NULL CHECK (status IN ('CONFIRMED','CONTESTED','REJECTED')),
    evidence_refs_json JSONB NOT NULL DEFAULT '[]',
    decision_authority VARCHAR(160) NOT NULL,
    reason VARCHAR(500),
    community_sequence BIGINT NOT NULL,
    UNIQUE (community_id, participant_id),
    UNIQUE (tenant_id, id)
);

CREATE TABLE ostris.transaction_proposal (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES idax_core.tenant(tenant_id),
    community_id UUID NOT NULL REFERENCES ostris.community(id),
    unit_id UUID NOT NULL REFERENCES ostris.unit_of_account(id),
    purpose VARCHAR(24) NOT NULL CHECK (purpose IN ('EXCHANGE','REVERSAL','SETTLEMENT','WRITE_OFF','RESTITUTION','PENALTY','LOSS_OFFSET')),
    entries_json JSONB NOT NULL,
    references_json JSONB NOT NULL DEFAULT '{}',
    contractual_metadata_digest VARCHAR(64),
    resolution_basis_type VARCHAR(40) CHECK (resolution_basis_type IS NULL OR resolution_basis_type IN ('FINAL_FINDING','FINAL_DISPUTE_RESOLUTION','FINAL_DEFAULT_DECISION','COMMUNITY_GOVERNANCE_DECISION')),
    resolution_basis_id UUID,
    authorization_payload TEXT NOT NULL,
    authorization_digest VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PROPOSED' CHECK (status IN ('PROPOSED','COMMITTED','REJECTED')),
    committed_sequence BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((resolution_basis_type IS NULL) = (resolution_basis_id IS NULL)),
    UNIQUE (tenant_id,id)
);

CREATE TABLE ostris.resolution_basis (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES idax_core.tenant(tenant_id),
    community_id UUID NOT NULL REFERENCES ostris.community(id),
    basis_type VARCHAR(40) NOT NULL CHECK (basis_type IN ('FINAL_FINDING','FINAL_DISPUTE_RESOLUTION','FINAL_DEFAULT_DECISION','COMMUNITY_GOVERNANCE_DECISION')),
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING','FINAL','OVERTURNED')),
    finality_sequence BIGINT,
    policy_version_id UUID REFERENCES ostris.policy_version(id),
    UNIQUE (tenant_id,id)
);

CREATE TABLE ostris.community_resolution_authority (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES idax_core.tenant(tenant_id),
    community_id UUID NOT NULL REFERENCES ostris.community(id),
    authority_version INTEGER NOT NULL CHECK (authority_version>0),
    threshold SMALLINT NOT NULL CHECK (threshold>0),
    activation_sequence BIGINT NOT NULL,
    revocation_sequence BIGINT,
    UNIQUE (community_id,authority_version), UNIQUE (tenant_id,id)
);

CREATE TABLE ostris.community_resolution_controller (
    tenant_id UUID NOT NULL REFERENCES idax_core.tenant(tenant_id),
    authority_id UUID NOT NULL REFERENCES ostris.community_resolution_authority(id),
    controller_id UUID NOT NULL REFERENCES ostris.protocol_controller(id),
    PRIMARY KEY(authority_id,controller_id)
);

CREATE TABLE ostris.journal_transaction (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES idax_core.tenant(tenant_id),
    community_id UUID NOT NULL REFERENCES ostris.community(id),
    unit_id UUID NOT NULL REFERENCES ostris.unit_of_account(id),
    community_sequence BIGINT NOT NULL CHECK (community_sequence > 0),
    purpose VARCHAR(24) NOT NULL CHECK (purpose IN ('EXCHANGE','REVERSAL','SETTLEMENT','WRITE_OFF','RESTITUTION','PENALTY','LOSS_OFFSET')),
    references_json JSONB NOT NULL DEFAULT '{}',
    policy_snapshot_json JSONB NOT NULL,
    canonical_json TEXT NOT NULL,
    protocol_digest VARCHAR(64) NOT NULL CHECK (protocol_digest ~ '^[0-9a-f]{64}$'),
    committed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    status VARCHAR(16) NOT NULL DEFAULT 'COMMITTED' CHECK (status = 'COMMITTED'),
    UNIQUE (community_id, community_sequence),
    UNIQUE (tenant_id, id)
);

CREATE TABLE ostris.journal_entry (
    transaction_id UUID NOT NULL REFERENCES ostris.journal_transaction(id),
    entry_index INTEGER NOT NULL CHECK (entry_index >= 0),
    tenant_id UUID NOT NULL REFERENCES idax_core.tenant(tenant_id),
    community_id UUID NOT NULL REFERENCES ostris.community(id),
    account_id UUID NOT NULL REFERENCES ostris.account(id),
    amount NUMERIC(78,0) NOT NULL CHECK (amount <> 0),
    PRIMARY KEY (transaction_id, entry_index),
    UNIQUE (transaction_id, account_id)
);

CREATE TABLE ostris.authorization_evidence (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES idax_core.tenant(tenant_id),
    community_id UUID NOT NULL REFERENCES ostris.community(id),
    proposal_id UUID NOT NULL REFERENCES ostris.transaction_proposal(id),
    account_id UUID NOT NULL REFERENCES ostris.account(id),
    credential_id UUID NOT NULL REFERENCES ostris.credential(id),
    authorization_digest VARCHAR(64) NOT NULL CHECK (authorization_digest ~ '^[0-9a-f]{64}$'),
    signature_base64url VARCHAR(128) NOT NULL,
    public_key_base64url VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (proposal_id, account_id, credential_id)
);

CREATE TABLE ostris.governance_authorization_evidence (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES idax_core.tenant(tenant_id),
    community_id UUID NOT NULL REFERENCES ostris.community(id),
    proposal_id UUID NOT NULL REFERENCES ostris.transaction_proposal(id),
    authority_id UUID NOT NULL REFERENCES ostris.community_resolution_authority(id),
    authority_version INTEGER NOT NULL,
    resolution_basis_id UUID NOT NULL REFERENCES ostris.resolution_basis(id),
    resolution_basis_type VARCHAR(40) NOT NULL,
    covered_accounts_json JSONB NOT NULL,
    credential_id UUID NOT NULL REFERENCES ostris.credential(id),
    public_key_base64url VARCHAR(64) NOT NULL,
    authorization_digest VARCHAR(64) NOT NULL,
    signature_base64url VARCHAR(128) NOT NULL,
    governance_payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(proposal_id,authority_id,credential_id)
);

CREATE TABLE ostris.protocol_proof_outbox (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES idax_core.tenant(tenant_id),
    community_id UUID NOT NULL REFERENCES ostris.community(id),
    transaction_id UUID NOT NULL UNIQUE REFERENCES ostris.journal_transaction(id),
    protocol_digest VARCHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL CHECK (status IN ('PENDING','ANCHORED','FAILED_RETRYABLE','DISABLED')),
    proof_reference VARCHAR(255),
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Tenant-scoped referential integrity: RLS limits visibility; these composite
-- constraints additionally make cross-tenant references impossible.
ALTER TABLE ostris.unit_of_account ADD CONSTRAINT fk_unit_community_tenant FOREIGN KEY(tenant_id,community_id) REFERENCES ostris.community(tenant_id,id);
ALTER TABLE ostris.risk_subject ADD CONSTRAINT fk_risk_community_tenant FOREIGN KEY(tenant_id,community_id) REFERENCES ostris.community(tenant_id,id);
ALTER TABLE ostris.participant ADD CONSTRAINT fk_participant_community_tenant FOREIGN KEY(tenant_id,community_id) REFERENCES ostris.community(tenant_id,id), ADD CONSTRAINT fk_participant_risk_tenant FOREIGN KEY(tenant_id,risk_subject_id) REFERENCES ostris.risk_subject(tenant_id,id);
ALTER TABLE ostris.identity_assurance_claim ADD CONSTRAINT fk_claim_community_tenant FOREIGN KEY(tenant_id,community_id) REFERENCES ostris.community(tenant_id,id), ADD CONSTRAINT fk_claim_risk_tenant FOREIGN KEY(tenant_id,risk_subject_id) REFERENCES ostris.risk_subject(tenant_id,id);
ALTER TABLE ostris.account ADD CONSTRAINT fk_account_community_tenant FOREIGN KEY(tenant_id,community_id) REFERENCES ostris.community(tenant_id,id), ADD CONSTRAINT fk_account_unit_tenant FOREIGN KEY(tenant_id,unit_id) REFERENCES ostris.unit_of_account(tenant_id,id), ADD CONSTRAINT fk_account_participant_tenant FOREIGN KEY(tenant_id,participant_id) REFERENCES ostris.participant(tenant_id,id);
ALTER TABLE ostris.credential ADD CONSTRAINT fk_credential_community_tenant FOREIGN KEY(tenant_id,community_id) REFERENCES ostris.community(tenant_id,id);
ALTER TABLE ostris.protocol_controller ADD CONSTRAINT fk_controller_community_tenant FOREIGN KEY(tenant_id,community_id) REFERENCES ostris.community(tenant_id,id);
ALTER TABLE ostris.controller_credential_binding ADD CONSTRAINT fk_binding_community_tenant FOREIGN KEY(tenant_id,community_id) REFERENCES ostris.community(tenant_id,id), ADD CONSTRAINT fk_binding_controller_tenant FOREIGN KEY(tenant_id,controller_id) REFERENCES ostris.protocol_controller(tenant_id,id), ADD CONSTRAINT fk_binding_credential_tenant FOREIGN KEY(tenant_id,credential_id) REFERENCES ostris.credential(tenant_id,id);
ALTER TABLE ostris.account_control_policy ADD CONSTRAINT fk_control_community_tenant FOREIGN KEY(tenant_id,community_id) REFERENCES ostris.community(tenant_id,id), ADD CONSTRAINT fk_control_account_tenant FOREIGN KEY(tenant_id,account_id) REFERENCES ostris.account(tenant_id,id);
ALTER TABLE ostris.policy_version ADD CONSTRAINT fk_policy_community_tenant FOREIGN KEY(tenant_id,community_id) REFERENCES ostris.community(tenant_id,id);
ALTER TABLE ostris.finding ADD CONSTRAINT fk_finding_community_tenant FOREIGN KEY(tenant_id,community_id) REFERENCES ostris.community(tenant_id,id);
ALTER TABLE ostris.appeal ADD CONSTRAINT fk_appeal_community_tenant FOREIGN KEY(tenant_id,community_id) REFERENCES ostris.community(tenant_id,id), ADD CONSTRAINT fk_appeal_finding_tenant FOREIGN KEY(tenant_id,finding_id) REFERENCES ostris.finding(tenant_id,id);
ALTER TABLE ostris.identity_continuity_decision ADD CONSTRAINT fk_continuity_community_tenant FOREIGN KEY(tenant_id,community_id) REFERENCES ostris.community(tenant_id,id), ADD CONSTRAINT fk_continuity_risk_tenant FOREIGN KEY(tenant_id,risk_subject_id) REFERENCES ostris.risk_subject(tenant_id,id), ADD CONSTRAINT fk_continuity_participant_tenant FOREIGN KEY(tenant_id,participant_id) REFERENCES ostris.participant(tenant_id,id);
ALTER TABLE ostris.transaction_proposal ADD CONSTRAINT fk_proposal_community_tenant FOREIGN KEY(tenant_id,community_id) REFERENCES ostris.community(tenant_id,id), ADD CONSTRAINT fk_proposal_unit_tenant FOREIGN KEY(tenant_id,unit_id) REFERENCES ostris.unit_of_account(tenant_id,id), ADD CONSTRAINT fk_proposal_basis_tenant FOREIGN KEY(tenant_id,resolution_basis_id) REFERENCES ostris.resolution_basis(tenant_id,id);
ALTER TABLE ostris.resolution_basis ADD CONSTRAINT fk_basis_community_tenant FOREIGN KEY(tenant_id,community_id) REFERENCES ostris.community(tenant_id,id);
ALTER TABLE ostris.community_resolution_authority ADD CONSTRAINT fk_authority_community_tenant FOREIGN KEY(tenant_id,community_id) REFERENCES ostris.community(tenant_id,id);
ALTER TABLE ostris.journal_transaction ADD CONSTRAINT fk_tx_community_tenant FOREIGN KEY(tenant_id,community_id) REFERENCES ostris.community(tenant_id,id), ADD CONSTRAINT fk_tx_unit_tenant FOREIGN KEY(tenant_id,unit_id) REFERENCES ostris.unit_of_account(tenant_id,id);
ALTER TABLE ostris.journal_entry ADD CONSTRAINT fk_entry_community_tenant FOREIGN KEY(tenant_id,community_id) REFERENCES ostris.community(tenant_id,id), ADD CONSTRAINT fk_entry_account_tenant FOREIGN KEY(tenant_id,account_id) REFERENCES ostris.account(tenant_id,id);
ALTER TABLE ostris.authorization_evidence ADD CONSTRAINT fk_auth_community_tenant FOREIGN KEY(tenant_id,community_id) REFERENCES ostris.community(tenant_id,id), ADD CONSTRAINT fk_auth_proposal_tenant FOREIGN KEY(tenant_id,proposal_id) REFERENCES ostris.transaction_proposal(tenant_id,id), ADD CONSTRAINT fk_auth_account_tenant FOREIGN KEY(tenant_id,account_id) REFERENCES ostris.account(tenant_id,id), ADD CONSTRAINT fk_auth_credential_tenant FOREIGN KEY(tenant_id,credential_id) REFERENCES ostris.credential(tenant_id,id);
ALTER TABLE ostris.governance_authorization_evidence ADD CONSTRAINT fk_gov_auth_community_tenant FOREIGN KEY(tenant_id,community_id) REFERENCES ostris.community(tenant_id,id), ADD CONSTRAINT fk_gov_auth_proposal_tenant FOREIGN KEY(tenant_id,proposal_id) REFERENCES ostris.transaction_proposal(tenant_id,id), ADD CONSTRAINT fk_gov_auth_authority_tenant FOREIGN KEY(tenant_id,authority_id) REFERENCES ostris.community_resolution_authority(tenant_id,id), ADD CONSTRAINT fk_gov_auth_basis_tenant FOREIGN KEY(tenant_id,resolution_basis_id) REFERENCES ostris.resolution_basis(tenant_id,id), ADD CONSTRAINT fk_gov_auth_credential_tenant FOREIGN KEY(tenant_id,credential_id) REFERENCES ostris.credential(tenant_id,id);
ALTER TABLE ostris.protocol_proof_outbox ADD CONSTRAINT fk_outbox_community_tenant FOREIGN KEY(tenant_id,community_id) REFERENCES ostris.community(tenant_id,id), ADD CONSTRAINT fk_outbox_tx_tenant FOREIGN KEY(tenant_id,transaction_id) REFERENCES ostris.journal_transaction(tenant_id,id);

CREATE INDEX idx_ostris_entries_account ON ostris.journal_entry(account_id);
CREATE INDEX idx_ostris_tx_community_sequence ON ostris.journal_transaction(community_id, community_sequence);
CREATE INDEX idx_ostris_policy_effective ON ostris.policy_version(community_id, policy_type, activation_sequence);

CREATE OR REPLACE FUNCTION ostris.reject_journal_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'osTRIS committed journal is append-only'; END $$;
CREATE TRIGGER trg_ostris_tx_immutable BEFORE UPDATE OR DELETE ON ostris.journal_transaction FOR EACH ROW EXECUTE FUNCTION ostris.reject_journal_mutation();
CREATE TRIGGER trg_ostris_entry_immutable BEFORE UPDATE OR DELETE ON ostris.journal_entry FOR EACH ROW EXECUTE FUNCTION ostris.reject_journal_mutation();
CREATE TRIGGER trg_ostris_auth_immutable BEFORE UPDATE OR DELETE ON ostris.authorization_evidence FOR EACH ROW EXECUTE FUNCTION ostris.reject_journal_mutation();
CREATE TRIGGER trg_ostris_gov_auth_immutable BEFORE UPDATE OR DELETE ON ostris.governance_authorization_evidence FOR EACH ROW EXECUTE FUNCTION ostris.reject_journal_mutation();

CREATE OR REPLACE FUNCTION ostris.protect_proposal_intent() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' OR NEW.id IS DISTINCT FROM OLD.id OR NEW.tenant_id IS DISTINCT FROM OLD.tenant_id
    OR NEW.community_id IS DISTINCT FROM OLD.community_id OR NEW.unit_id IS DISTINCT FROM OLD.unit_id
    OR NEW.purpose IS DISTINCT FROM OLD.purpose OR NEW.entries_json IS DISTINCT FROM OLD.entries_json
    OR NEW.references_json IS DISTINCT FROM OLD.references_json
    OR NEW.contractual_metadata_digest IS DISTINCT FROM OLD.contractual_metadata_digest
    OR NEW.resolution_basis_type IS DISTINCT FROM OLD.resolution_basis_type
    OR NEW.resolution_basis_id IS DISTINCT FROM OLD.resolution_basis_id
    OR NEW.authorization_payload IS DISTINCT FROM OLD.authorization_payload
    OR NEW.authorization_digest IS DISTINCT FROM OLD.authorization_digest
    OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
   RAISE EXCEPTION 'osTRIS transaction proposal intent is immutable';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_ostris_proposal_intent_immutable BEFORE UPDATE OR DELETE ON ostris.transaction_proposal FOR EACH ROW EXECUTE FUNCTION ostris.protect_proposal_intent();

DO $$ DECLARE t text; BEGIN
 FOREACH t IN ARRAY ARRAY['community','unit_of_account','risk_subject','participant','identity_assurance_claim','account','credential','protocol_controller','controller_credential_binding','account_control_policy','policy_version','finding','appeal','identity_continuity_decision','transaction_proposal','resolution_basis','community_resolution_authority','journal_transaction','journal_entry','authorization_evidence','governance_authorization_evidence','protocol_proof_outbox'] LOOP
   EXECUTE format('ALTER TABLE ostris.%I ENABLE ROW LEVEL SECURITY', t);
   EXECUTE format('CREATE POLICY p_%s_admin ON ostris.%I FOR ALL TO idax_admin USING (true) WITH CHECK (true)', t, t);
   EXECUTE format('CREATE POLICY p_%s_app ON ostris.%I FOR ALL TO idax_app USING (tenant_id = NULLIF(current_setting(''app.tenant_id'',true),'''')::uuid) WITH CHECK (tenant_id = NULLIF(current_setting(''app.tenant_id'',true),'''')::uuid)', t, t);
 END LOOP;
END $$;

ALTER TABLE ostris.account_controller ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_account_controller_admin ON ostris.account_controller FOR ALL TO idax_admin USING (true) WITH CHECK (true);
CREATE POLICY p_account_controller_app ON ostris.account_controller FOR ALL TO idax_app
USING (EXISTS (SELECT 1 FROM ostris.account_control_policy p WHERE p.id=control_policy_id AND p.tenant_id=NULLIF(current_setting('app.tenant_id',true),'')::uuid))
WITH CHECK (EXISTS (SELECT 1 FROM ostris.account_control_policy p WHERE p.id=control_policy_id AND p.tenant_id=NULLIF(current_setting('app.tenant_id',true),'')::uuid));

ALTER TABLE ostris.community_resolution_controller ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_resolution_controller_admin ON ostris.community_resolution_controller FOR ALL TO idax_admin USING (true) WITH CHECK (true);
CREATE POLICY p_resolution_controller_app ON ostris.community_resolution_controller FOR ALL TO idax_app
USING (EXISTS (SELECT 1 FROM ostris.community_resolution_authority a WHERE a.id=authority_id AND a.tenant_id=NULLIF(current_setting('app.tenant_id',true),'')::uuid))
WITH CHECK (EXISTS (SELECT 1 FROM ostris.community_resolution_authority a WHERE a.id=authority_id AND a.tenant_id=NULLIF(current_setting('app.tenant_id',true),'')::uuid));

ALTER TABLE ostris.account_controller ADD CONSTRAINT fk_account_controller_policy_tenant FOREIGN KEY(tenant_id,control_policy_id) REFERENCES ostris.account_control_policy(tenant_id,id), ADD CONSTRAINT fk_account_controller_controller_tenant FOREIGN KEY(tenant_id,controller_id) REFERENCES ostris.protocol_controller(tenant_id,id);
ALTER TABLE ostris.community_resolution_controller ADD CONSTRAINT fk_resolution_controller_authority_tenant FOREIGN KEY(tenant_id,authority_id) REFERENCES ostris.community_resolution_authority(tenant_id,id), ADD CONSTRAINT fk_resolution_controller_controller_tenant FOREIGN KEY(tenant_id,controller_id) REFERENCES ostris.protocol_controller(tenant_id,id);

GRANT USAGE ON SCHEMA ostris TO idax_app, idax_admin;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA ostris TO idax_admin;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA ostris TO idax_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA ostris GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO idax_app, idax_admin;
