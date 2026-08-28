ALTER TABLE ostris.protocol_proof_outbox
    ADD COLUMN proof_digest VARCHAR(64),
    ADD COLUMN external_reference VARCHAR(74),
    ADD COLUMN canonicalization_profile VARCHAR(100),
    ADD COLUMN claim_token UUID,
    ADD COLUMN claimed_at TIMESTAMPTZ,
    ADD COLUMN last_error VARCHAR(1000);

ALTER TABLE ostris.protocol_proof_outbox
    DROP CONSTRAINT protocol_proof_outbox_status_check,
    ADD CONSTRAINT protocol_proof_outbox_status_check
        CHECK (status IN ('PENDING','PROCESSING','ANCHORED','FAILED_RETRYABLE','FAILED_PERMANENT','DISABLED')),
    ADD CONSTRAINT ck_protocol_proof_digest
        CHECK (proof_digest IS NULL OR proof_digest ~ '^[0-9a-f]{64}$'),
    ADD CONSTRAINT ck_protocol_proof_external_reference
        CHECK (external_reference IS NULL OR external_reference ~ '^ostris:v1:[0-9a-f]{64}$'),
    ADD CONSTRAINT ck_protocol_proof_profile
        CHECK (canonicalization_profile IS NULL OR canonicalization_profile = 'EXTERNAL:OSTRIS-PROTOCOL-EVENT-PROOF-V1'),
    ADD CONSTRAINT ck_protocol_proof_claim
        CHECK ((status = 'PROCESSING') = (claim_token IS NOT NULL AND claimed_at IS NOT NULL));

CREATE INDEX idx_protocol_proof_outbox_delivery
    ON ostris.protocol_proof_outbox(status, next_retry_at, created_at);
