package es.idynamicsax.ostris.ledger;

import es.idynamicsax.ostris.proof.CommittedJournalTransaction;
import java.util.Optional;
import java.util.UUID;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class ProtocolProofOutboxStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final OstrisLedgerDeliveryProperties properties;

    public ProtocolProofOutboxStore(JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
                                    Clock clock, OstrisLedgerDeliveryProperties properties) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.properties = properties;
    }

    public Optional<ClaimedProof> claimNext() {
        UUID token = UUID.randomUUID();
        Instant now = clock.instant();
        Instant leaseBoundary = now.minus(properties.processingLease());
        Optional<ClaimedProof> claimed = transactions.execute(status -> jdbc.query("""
                WITH candidate AS (
                    SELECT id FROM ostris.protocol_proof_outbox
                    WHERE (status = 'PENDING')
                       OR (status = 'FAILED_RETRYABLE' AND (next_retry_at IS NULL OR next_retry_at <= ?))
                       OR (status = 'PROCESSING' AND claimed_at <= ?)
                    ORDER BY created_at
                    FOR UPDATE SKIP LOCKED
                    LIMIT 1
                )
                UPDATE ostris.protocol_proof_outbox o
                SET status='PROCESSING', claim_token=?, claimed_at=?, next_retry_at=NULL,
                    attempt_count=o.attempt_count+1, last_error=NULL
                FROM candidate c WHERE o.id=c.id
                RETURNING o.id,o.tenant_id,o.community_id,o.transaction_id,o.protocol_digest,
                          o.proof_digest,o.external_reference,o.canonicalization_profile,o.claim_token,
                          o.proof_reference,o.attempt_count
                """, (rs, row) -> new ClaimedProof(
                rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getObject("community_id", UUID.class), rs.getObject("transaction_id", UUID.class),
                rs.getString("protocol_digest"), rs.getString("proof_digest"),
                rs.getString("external_reference"), rs.getString("canonicalization_profile"),
                rs.getObject("claim_token", UUID.class), rs.getString("proof_reference"),
                rs.getInt("attempt_count")), Timestamp.from(now), Timestamp.from(leaseBoundary), token,
                Timestamp.from(now)).stream().findFirst());
        return claimed == null ? Optional.empty() : claimed;
    }

    public CommittedJournalTransaction loadJournal(ClaimedProof claim) {
        return jdbc.queryForObject("""
                SELECT tenant_id,community_id,unit_id,id,community_sequence,status,canonical_json,protocol_digest
                FROM ostris.journal_transaction
                WHERE tenant_id=? AND community_id=? AND id=?
                """, (rs, row) -> new CommittedJournalTransaction(
                rs.getObject("tenant_id", UUID.class), rs.getObject("community_id", UUID.class),
                rs.getObject("unit_id", UUID.class), rs.getObject("id", UUID.class),
                rs.getLong("community_sequence"), rs.getString("status"),
                rs.getString("canonical_json"), rs.getString("protocol_digest")),
                claim.tenantId(), claim.communityId(), claim.transactionId());
    }

    public boolean proofCreated(ClaimedProof claim, UUID proofId) {
        return updateClaim(claim, "proof_reference=?, last_error=NULL", proofId.toString());
    }

    public boolean anchored(ClaimedProof claim, UUID proofId) {
        return updateClaim(claim, "status='ANCHORED', proof_reference=?, claim_token=NULL, claimed_at=NULL, next_retry_at=NULL, last_error=NULL", proofId.toString());
    }

    public boolean failed(ClaimedProof claim, boolean permanent, String code) {
        return failed(claim, permanent, code, null);
    }

    public boolean failed(ClaimedProof claim, boolean permanent, String code, Duration minimumDelay) {
        String safe = sanitize(code);
        if (permanent) {
            return updateClaim(claim, "status='FAILED_PERMANENT', claim_token=NULL, claimed_at=NULL, next_retry_at=NULL, last_error=?", safe);
        } else {
            Duration delay = retryDelay(claim.attemptCount());
            if (minimumDelay != null && minimumDelay.compareTo(delay) > 0) delay = minimumDelay;
            Instant retryAt = clock.instant().plus(delay);
            return updateClaim(claim, "status='FAILED_RETRYABLE', claim_token=NULL, claimed_at=NULL, next_retry_at=?, last_error=?", Timestamp.from(retryAt), safe);
        }
    }

    public void disabledPending() {
        transactions.executeWithoutResult(status -> jdbc.update("""
                UPDATE ostris.protocol_proof_outbox
                SET status='DISABLED', claim_token=NULL, claimed_at=NULL
                WHERE status IN ('PENDING','FAILED_RETRYABLE')
                """));
    }

    private boolean updateClaim(ClaimedProof claim, String assignment, Object... values) {
        Boolean updated = transactions.execute(status -> {
            Object[] parameters = new Object[values.length + 2];
            System.arraycopy(values, 0, parameters, 0, values.length);
            parameters[values.length] = claim.id();
            parameters[values.length + 1] = claim.claimToken();
            int changed = jdbc.update("UPDATE ostris.protocol_proof_outbox SET " + assignment + " WHERE id=? AND claim_token=? AND status='PROCESSING'", parameters);
            return changed == 1;
        });
        return Boolean.TRUE.equals(updated);
    }

    Duration retryDelay(int attemptNumber) {
        long initial = properties.initialRetryDelay().toMillis();
        int exponent = Math.max(0, Math.min(62, attemptNumber - 1));
        long multiplier = 1L << Math.min(30, exponent);
        long delay;
        try { delay = Math.multiplyExact(initial, multiplier); }
        catch (ArithmeticException overflow) { delay = Long.MAX_VALUE; }
        return Duration.ofMillis(Math.min(delay, properties.maximumRetryDelay().toMillis()));
    }

    private String sanitize(String value) {
        String safe = value == null ? "LEDGER_DELIVERY_FAILED" : value.replaceAll("[^A-Za-z0-9_.:-]", "_");
        return safe.substring(0, Math.min(safe.length(), 160));
    }

    public record ClaimedProof(UUID id, UUID tenantId, UUID communityId, UUID transactionId,
                               String protocolDigest, String proofDigest, String externalReference,
                               String canonicalizationProfile, UUID claimToken, String proofReference,
                               int attemptCount) {}
}
