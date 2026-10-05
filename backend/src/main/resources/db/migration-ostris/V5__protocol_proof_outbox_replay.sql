-- Reviewed, explicit-operator replay for a FAILED_PERMANENT ledger delivery (see
-- ProtocolProofOutboxStore.replayFailedProof). This table is written by application code
-- exactly once per replay call, as an INSERT immediately before the outbox row is requeued to
-- PENDING - never UPDATEd or DELETEd - so the original failure (status/last_error/attempt_count
-- at the moment of replay) stays readable even after the outbox row's own last_error is
-- overwritten by the next delivery attempt's outcome.
CREATE TABLE ostris.protocol_proof_outbox_replay_event (
    id UUID PRIMARY KEY,
    outbox_id UUID NOT NULL REFERENCES ostris.protocol_proof_outbox(id),
    tenant_id UUID NOT NULL,
    replayed_by VARCHAR(200) NOT NULL,
    replayed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    reason VARCHAR(500),
    previous_status VARCHAR(30) NOT NULL,
    previous_last_error VARCHAR(1000),
    previous_attempt_count INT NOT NULL
);

CREATE INDEX idx_protocol_proof_outbox_replay_event_outbox
    ON ostris.protocol_proof_outbox_replay_event(outbox_id, replayed_at);
