package es.idynamicsax.ostris.ledger;

import es.idynamicsax.idax.service.auth.ServiceTokenProvider;
import es.idynamicsax.ostris.core.OstrisWireCodec;
import es.idynamicsax.ostris.proof.ProtocolEventProofBuilder;
import es.idynamicsax.ostris.proof.ProtocolEventProofV1;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;

@Service
public class ProtocolEventProofDeliveryService {
    public static final String PROOF_TYPE = "OSTRIS_PROTOCOL_EVENT_PROOF_V1";
    public static final Set<String> PERMISSIONS = Set.of("LEDGER_PROOF_CREATE", "LEDGER_READ", "LEDGER_PROOF_VERIFY");
    private final ProtocolProofOutboxStore outbox;
    private final ProtocolEventProofBuilder builder;
    private final ServiceTokenProvider tokens;
    private final LedgerProofClient ledger;
    private final OstrisLedgerDeliveryProperties properties;
    private final ProtocolProofDeliveryMetrics metrics;
    private final java.time.Clock clock;

    public ProtocolEventProofDeliveryService(ProtocolProofOutboxStore outbox,
                                             com.fasterxml.jackson.databind.ObjectMapper mapper,
                                             ServiceTokenProvider tokens,
                                             LedgerProofClient ledger,
                                             OstrisLedgerDeliveryProperties properties,
                                             ProtocolProofDeliveryMetrics metrics,
                                             java.time.Clock clock) {
        this.outbox = outbox;
        this.builder = new ProtocolEventProofBuilder(mapper, new OstrisWireCodec());
        this.tokens = tokens;
        this.ledger = ledger;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
    }

    public boolean deliverNext() {
        if (!properties.enabled()) {
            outbox.disabledPending();
            return false;
        }
        var claim = outbox.claimNext();
        if (claim.isEmpty()) return false;
        metrics.attempt();
        deliver(claim.get());
        return true;
    }

    private void deliver(ProtocolProofOutboxStore.ClaimedProof claim) {
        try {
            ProtocolEventProofV1 proof = builder.build(outbox.loadJournal(claim));
            requireEqual("protocolDigest", claim.protocolDigest(), proof.transactionDigest());
            requireEqual("proofDigest", claim.proofDigest(), proof.proofDigest());
            requireEqual("externalReference", claim.externalReference(), proof.externalReference());
            requireEqual("canonicalizationProfile", claim.canonicalizationProfile(), proof.canonicalizationProfile());
            var request = new LedgerProofClient.LedgerProofRequest(
                    proof.externalReference(), PROOF_TYPE, "SHA-256", proof.proofDigest(),
                    proof.canonicalizationProfile(), Map.of());
            String token = tokens.token(claim.tenantId(), properties.audience(), PERMISSIONS).accessToken();
            var ledgerProof = resolveProof(claim, token, request, proof.externalReference());
            if (ledgerProof == null) { metrics.staleResult(); return; }
            String ledgerStatus = ledgerProof.submission() == null
                    ? ledgerProof.status() : ledgerProof.submission().status();
            if (Set.of("FAILED_PERMANENT", "ATTEMPT_EXPIRED").contains(ledgerStatus)) {
                fail(claim, true, "LEDGER_" + ledgerStatus);
                return;
            }
            if (Set.of("PENDING", "SIGNED", "SUBMITTED", "FAILED_RETRYABLE").contains(ledgerStatus)) {
                fail(claim, false, "LEDGER_" + ledgerStatus);
                return;
            }
            var verified = ledger.verify(token, ledgerProof.id(), request);
            classifyVerification(claim, ledgerProof.id(), verified);
        } catch (HttpStatusCodeException http) {
            int status = http.getStatusCode().value();
            boolean permanent = status == 400 || status == 401 || status == 403 || status == 404 || status == 409;
            String classification = status == 409 ? "IDEMPOTENCY_CONFLICT" : "LEDGER_HTTP_" + status;
            if (status == 429) fail(claim, false, classification, retryAfter(http));
            else fail(claim, permanent, classification);
        } catch (IntegrityException integrity) {
            fail(claim, true, integrity.getMessage());
        } catch (Exception failure) {
            boolean malformed = failure instanceof IllegalStateException;
            fail(claim, malformed, malformed ? "MALFORMED_LEDGER_RESPONSE" : "LEDGER_" + failure.getClass().getSimpleName());
        }
    }

    private LedgerProofClient.CreatedProof resolveProof(
            ProtocolProofOutboxStore.ClaimedProof claim, String token,
            LedgerProofClient.LedgerProofRequest request, String idempotencyKey) {
        LedgerProofClient.CreatedProof proof;
        if (claim.proofReference() != null) {
            UUID proofId;
            try { proofId = UUID.fromString(claim.proofReference()); }
            catch (IllegalArgumentException invalid) { throw new IntegrityException("INVALID_PROOF_REFERENCE"); }
            proof = ledger.get(token, proofId);
        } else {
            proof = ledger.create(token, idempotencyKey, request);
            if (!outbox.proofCreated(claim, proof.id())) return null;
        }
        requireEqual("ledgerExternalId", request.externalId(), proof.externalId());
        requireEqual("ledgerProofDigest", request.hash(), proof.contentHash());
        requireEqual("ledgerCanonicalizationProfile", request.canonicalizationProfile(), proof.canonicalizationProfile());
        return proof;
    }

    private void classifyVerification(ProtocolProofOutboxStore.ClaimedProof claim, UUID proofId,
                                      LedgerProofClient.Verification verified) {
        if (verified.integrity() == null || verified.ledger() == null) {
            throw new IllegalStateException("Ledger returned incomplete verification");
        }
        if (!"MATCH".equals(verified.integrity().status())) {
            fail(claim, true, "LEDGER_INTEGRITY_MISMATCH");
            return;
        }
        switch (verified.ledger().status()) {
            case "VALIDATED_MATCH" -> {
                if (outbox.anchored(claim, proofId)) metrics.success(); else metrics.staleResult();
            }
            case "NOT_FOUND", "NOT_VALIDATED", "PROVIDER_UNAVAILABLE" ->
                    fail(claim, false, "VERIFY_" + verified.ledger().status());
            case "ANCHOR_MISMATCH" -> fail(claim, true, "VERIFY_ANCHOR_MISMATCH");
            default -> fail(claim, true, "UNKNOWN_VERIFY_STATUS");
        }
    }

    private void fail(ProtocolProofOutboxStore.ClaimedProof claim, boolean permanent, String classification) {
        if (outbox.failed(claim, permanent, classification)) metrics.failure(permanent, classification);
        else metrics.staleResult();
    }

    private void fail(ProtocolProofOutboxStore.ClaimedProof claim, boolean permanent, String classification,
                      Duration minimumDelay) {
        if (outbox.failed(claim, permanent, classification, minimumDelay)) metrics.failure(permanent, classification);
        else metrics.staleResult();
    }

    private Duration retryAfter(HttpStatusCodeException http) {
        String value = http.getResponseHeaders() == null ? null : http.getResponseHeaders().getFirst("Retry-After");
        if (value == null || value.isBlank()) return null;
        try { return Duration.ofSeconds(Math.max(0, Long.parseLong(value.trim()))); }
        catch (NumberFormatException ignored) {
            try {
                return Duration.between(clock.instant(),
                        ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
            } catch (Exception invalid) { return null; }
        }
    }

    private void requireEqual(String name, String persisted, String reconstructed) {
        if (persisted == null || !persisted.equals(reconstructed)) {
            throw new IntegrityException(name + " does not match reconstructed journal proof");
        }
    }

    private static final class IntegrityException extends RuntimeException {
        private IntegrityException(String message) { super(message); }
    }
}
