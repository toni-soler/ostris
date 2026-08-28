CREATE INDEX idx_protocol_proof_outbox_lease_recovery
    ON ostris.protocol_proof_outbox(status, claimed_at, next_retry_at, created_at);
