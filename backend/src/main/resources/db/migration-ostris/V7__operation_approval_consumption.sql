-- Single-use record of per-operation approvals consumed by osTRIS.
--
-- A per-operation approval (IDAX Core OperationApprovalService) proves that the authenticated user
-- confirmed ONE specific operation with a fresh second factor. osTRIS is the authoritative boundary
-- for credential management, so osTRIS - not the issuer, not a proxy - records that an approval was
-- spent, in the same transaction as the operation it authorised: the approval is consumed if and
-- only if the credential change takes effect. The primary key makes the claim atomic across
-- threads, instances and connections; a second presentation of the same approval inserts nothing.
--
-- Rows carry no secret (no token, no code): only the approval id and what it was spent on. They are
-- append-only evidence; rows past expires_at can never be replayed (the verifier rejects an expired
-- approval before reaching this table) and may be pruned operationally.

CREATE TABLE ostris.operation_approval_consumption (
    approval_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES idax_core.tenant(tenant_id),
    user_id UUID NOT NULL,
    action VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ostris_approval_consumption_expiry ON ostris.operation_approval_consumption(expires_at);

CREATE TRIGGER trg_ostris_approval_consumption_immutable BEFORE UPDATE ON ostris.operation_approval_consumption
    FOR EACH ROW EXECUTE FUNCTION ostris.reject_journal_mutation();

ALTER TABLE ostris.operation_approval_consumption ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_operation_approval_consumption_admin ON ostris.operation_approval_consumption
    FOR ALL TO idax_admin USING (true) WITH CHECK (true);
CREATE POLICY p_operation_approval_consumption_app ON ostris.operation_approval_consumption
    FOR ALL TO idax_app
    USING (tenant_id = NULLIF(current_setting('app.tenant_id',true),'')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id',true),'')::uuid);

-- V1's default privileges already grant DML on new ostris tables to idax_app/idax_admin; consumed
-- approvals must not be rewritable or removable by the runtime roles.
REVOKE UPDATE, DELETE ON ostris.operation_approval_consumption FROM idax_app, idax_admin;
