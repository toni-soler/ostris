ALTER TABLE ostris.identity_continuity_decision
    DROP CONSTRAINT identity_continuity_decision_community_id_participant_id_key;

ALTER TABLE ostris.identity_continuity_decision
    ADD CONSTRAINT uq_continuity_participant_sequence
    UNIQUE (community_id, participant_id, community_sequence);

CREATE INDEX idx_continuity_effective
    ON ostris.identity_continuity_decision
       (tenant_id, community_id, participant_id, community_sequence DESC);

CREATE TRIGGER trg_ostris_continuity_immutable
    BEFORE UPDATE OR DELETE ON ostris.identity_continuity_decision
    FOR EACH ROW EXECUTE FUNCTION ostris.reject_journal_mutation();
