-- Controller ownership (closes the cross-participant credential-management finding).
--
-- Until now a ProtocolController recorded only tenant + community: osTRIS had NO notion of
-- WHICH authenticated IDAX user a controller belongs to. The public provisioning surface
-- therefore gated credential add/revoke on the tenant-wide permission OSTRIS_CREDENTIAL_MANAGE
-- alone, which a superuser satisfies unconditionally and which any holder satisfies for EVERY
-- controller in the tenant. The owning-user check lived only in STIR (it always passes its own
-- participant's controllerId); a caller reaching osTRIS directly could bind their own key to, or
-- revoke a device from, another participant's controller and then sign that participant's
-- transactions. See ostris-doc CONTROLLER_OWNERSHIP.md.
--
-- The fix makes the controller carry its owner, set at activation to the authenticated caller,
-- and enforced at the authoritative service (ProvisioningService) on every credential mutation -
-- never only in STIR or the proxy, and never waved through for a superuser.

ALTER TABLE ostris.protocol_controller
    ADD COLUMN owner_user_id UUID,
    ADD COLUMN owner_established_by VARCHAR(16)
        CHECK (owner_established_by IS NULL OR owner_established_by IN ('ACTIVATION')),
    ADD COLUMN owner_established_sequence BIGINT;

-- owner provenance is all-or-nothing: either a controller has a recorded owner with how/when, or
-- it is unowned (fail-closed for credential management). No partially-attributed state.
ALTER TABLE ostris.protocol_controller
    ADD CONSTRAINT ck_controller_owner_all_or_nothing CHECK (
        (owner_user_id IS NULL AND owner_established_by IS NULL AND owner_established_sequence IS NULL)
        OR
        (owner_user_id IS NOT NULL AND owner_established_by IS NOT NULL AND owner_established_sequence IS NOT NULL)
    );

CREATE INDEX idx_ostris_controller_owner ON ostris.protocol_controller(tenant_id, owner_user_id);

-- Deliberately NO automatic backfill of existing controllers.
--
-- osTRIS never recorded which IDAX user created a pre-existing controller, and a pre-existing
-- binding may itself be a product of the very vulnerability this migration closes. Inferring an
-- owner from current bindings would be exactly the ambiguous, first-possession appropriation the
-- fix forbids. Existing controllers therefore keep owner_user_id = NULL and are fail-closed:
-- ProvisioningService refuses credential add/revoke on an unowned controller
-- (CONTROLLER_OWNERSHIP_UNASSIGNED). Re-establishing ownership of a pre-existing controller is an
-- out-of-band operator/re-provision action (the system is pre-pilot), not an endpoint that could
-- be driven by whoever asks first; a governed re-attestation contract is a documented SPEC GAP.
--
-- Verifiable migration: after this runs, every controller is in exactly one of two states, and an
-- auditor can confirm it with a single query (no controller may be partially attributed - the
-- all-or-nothing CHECK above guarantees it):
--     SELECT owner_user_id IS NOT NULL AS owned, count(*)
--       FROM ostris.protocol_controller GROUP BY 1;
-- Pre-existing rows are 'owned=false' (locked); every controller created from now on is
-- 'owned=true' from its first moment (ProvisioningService.activateParticipant sets it in the same
-- transaction that creates the controller).
DO $$
DECLARE unowned BIGINT;
BEGIN
    SELECT count(*) INTO unowned FROM ostris.protocol_controller WHERE owner_user_id IS NULL;
    RAISE NOTICE 'V6 controller ownership: % pre-existing controller(s) left unowned and fail-closed for credential management', unowned;
END $$;
