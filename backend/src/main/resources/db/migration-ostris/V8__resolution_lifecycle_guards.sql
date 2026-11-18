-- Resolution authorities and resolution bases gained an application API (ResolutionService).
-- Until now only fixtures wrote them; these triggers make the lifecycle the API promises hold at
-- the database too, for every role:
--   * an authority version is immutable except for being revoked once;
--   * a resolution basis keeps its identity and type, moves PENDING -> FINAL -> OVERTURNED or
--     PENDING -> OVERTURNED only, and records its finality sequence exactly once;
--   * neither is ever deleted, and an authority's controller set never changes after creation.

CREATE OR REPLACE FUNCTION ostris.protect_resolution_authority() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP = 'DELETE' THEN RAISE EXCEPTION 'osTRIS resolution authority is never deleted'; END IF;
 IF NEW.id IS DISTINCT FROM OLD.id OR NEW.tenant_id IS DISTINCT FROM OLD.tenant_id
    OR NEW.community_id IS DISTINCT FROM OLD.community_id OR NEW.authority_version IS DISTINCT FROM OLD.authority_version
    OR NEW.threshold IS DISTINCT FROM OLD.threshold OR NEW.activation_sequence IS DISTINCT FROM OLD.activation_sequence
    OR (OLD.revocation_sequence IS NOT NULL AND NEW.revocation_sequence IS DISTINCT FROM OLD.revocation_sequence) THEN
   RAISE EXCEPTION 'osTRIS resolution authority is immutable except for a single revocation';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_ostris_resolution_authority_guard BEFORE UPDATE OR DELETE ON ostris.community_resolution_authority
 FOR EACH ROW EXECUTE FUNCTION ostris.protect_resolution_authority();

CREATE OR REPLACE FUNCTION ostris.reject_resolution_controller_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 RAISE EXCEPTION 'osTRIS resolution authority controllers are fixed per authority version';
END $$;
CREATE TRIGGER trg_ostris_resolution_controller_immutable BEFORE UPDATE OR DELETE ON ostris.community_resolution_controller
 FOR EACH ROW EXECUTE FUNCTION ostris.reject_resolution_controller_mutation();

CREATE OR REPLACE FUNCTION ostris.protect_resolution_basis() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP = 'DELETE' THEN RAISE EXCEPTION 'osTRIS resolution basis is never deleted'; END IF;
 IF NEW.id IS DISTINCT FROM OLD.id OR NEW.tenant_id IS DISTINCT FROM OLD.tenant_id
    OR NEW.community_id IS DISTINCT FROM OLD.community_id OR NEW.basis_type IS DISTINCT FROM OLD.basis_type
    OR NEW.policy_version_id IS DISTINCT FROM OLD.policy_version_id
    OR (OLD.finality_sequence IS NOT NULL AND NEW.finality_sequence IS DISTINCT FROM OLD.finality_sequence) THEN
   RAISE EXCEPTION 'osTRIS resolution basis identity is immutable';
 END IF;
 IF NEW.status IS DISTINCT FROM OLD.status AND NOT (
      (OLD.status = 'PENDING' AND NEW.status IN ('FINAL', 'OVERTURNED'))
   OR (OLD.status = 'FINAL' AND NEW.status = 'OVERTURNED')) THEN
   RAISE EXCEPTION 'osTRIS resolution basis cannot move from % to %', OLD.status, NEW.status;
 END IF;
 IF NEW.status = 'FINAL' AND NEW.finality_sequence IS NULL THEN
   RAISE EXCEPTION 'A FINAL resolution basis records its finality sequence';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_ostris_resolution_basis_guard BEFORE UPDATE OR DELETE ON ostris.resolution_basis
 FOR EACH ROW EXECUTE FUNCTION ostris.protect_resolution_basis();
