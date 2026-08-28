package es.idynamicsax.ostris.ledger;

import java.util.UUID;

public interface LedgerProofClient {
    CreatedProof create(String bearerToken, String idempotencyKey, LedgerProofRequest request);
    CreatedProof get(String bearerToken, UUID proofId);
    Verification verify(String bearerToken, UUID proofId, LedgerProofRequest request);

    record LedgerProofRequest(
            String externalId,
            String proofType,
            String hashAlgorithm,
            String hash,
            String canonicalizationProfile,
            java.util.Map<String, Object> metadata) {
    }

    record CreatedProof(UUID id, String externalId, String contentHash, String canonicalizationProfile,
                        String status, Submission submission) {
        public record Submission(String status, int attemptCount, String transactionHash,
                                 Long ledgerIndex, String ledgerHash) {}
    }

    record Verification(Integrity integrity, Ledger ledger) {
        public record Integrity(String status, String calculatedHash, String profile) {}
        public record Ledger(String status, String detail, String transactionHash, Long ledgerIndex, String ledgerHash) {}
    }
}
