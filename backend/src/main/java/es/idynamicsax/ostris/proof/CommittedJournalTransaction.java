package es.idynamicsax.ostris.proof;

import java.util.Objects;
import java.util.UUID;

/** Immutable journal state required to reconstruct a protocol event proof. */
public record CommittedJournalTransaction(
        UUID tenantId,
        UUID communityId,
        UUID unitId,
        UUID transactionId,
        long communitySequence,
        String status,
        String canonicalTransactionJson,
        String persistedProtocolDigest) {

    public CommittedJournalTransaction {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(communityId);
        Objects.requireNonNull(unitId);
        Objects.requireNonNull(transactionId);
        Objects.requireNonNull(status);
        Objects.requireNonNull(canonicalTransactionJson);
        Objects.requireNonNull(persistedProtocolDigest);
    }
}
