package es.idynamicsax.ostris.ledger;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public final class ProtocolProofDeliveryMetrics {
    private final MeterRegistry registry;

    public ProtocolProofDeliveryMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    void attempt() { registry.counter("ostris.ledger.delivery.attempts").increment(); }
    void success() { registry.counter("ostris.ledger.delivery.successes").increment(); }
    void staleResult() { registry.counter("ostris.ledger.delivery.stale_results").increment(); }
    void failure(boolean permanent, String classification) {
        registry.counter("ostris.ledger.delivery.failures", "permanent", Boolean.toString(permanent),
                "classification", safeTag(classification)).increment();
        if ("IDEMPOTENCY_CONFLICT".equals(classification))
            registry.counter("ostris.ledger.delivery.idempotency_conflicts").increment();
        if (classification != null && classification.contains("MISMATCH"))
            registry.counter("ostris.ledger.delivery.verification_mismatches").increment();
    }

    private String safeTag(String value) {
        if (value == null) return "UNKNOWN";
        String safe = value.replaceAll("[^A-Za-z0-9_.:-]", "_");
        return safe.substring(0, Math.min(safe.length(), 80));
    }
}
