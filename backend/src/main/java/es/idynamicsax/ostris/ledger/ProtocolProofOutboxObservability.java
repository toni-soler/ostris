package es.idynamicsax.ostris.ledger;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public final class ProtocolProofOutboxObservability implements MeterBinder, HealthIndicator {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ProtocolProofOutboxObservability(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        for (String status : new String[] {"PENDING", "PROCESSING", "FAILED_RETRYABLE", "FAILED_PERMANENT", "ANCHORED", "DISABLED"}) {
            Gauge.builder("ostris.ledger.outbox.rows", this, ignored -> count(status))
                    .tag("status", status).register(registry);
        }
        Gauge.builder("ostris.ledger.outbox.oldest_pending_age_seconds", this, ignored -> oldestAge("PENDING"))
                .register(registry);
        Gauge.builder("ostris.ledger.outbox.oldest_processing_age_seconds", this, ignored -> oldestAge("PROCESSING"))
                .register(registry);
    }

    @Override
    public Health health() {
        long retryable = count("FAILED_RETRYABLE");
        long permanent = count("FAILED_PERMANENT");
        long processing = count("PROCESSING");
        String delivery = retryable + permanent > 0 ? "DEGRADED" : "AVAILABLE";
        return Health.up().withDetail("economicProcessing", "AVAILABLE")
                .withDetail("ledgerDelivery", delivery)
                .withDetail("processing", processing)
                .withDetail("failedRetryable", retryable)
                .withDetail("failedPermanent", permanent).build();
    }

    private long count(String status) {
        Long value = jdbc.queryForObject("select count(*) from ostris.protocol_proof_outbox where status=?", Long.class, status);
        return value == null ? 0 : value;
    }

    private double oldestAge(String status) {
        Double value = jdbc.queryForObject("""
                select coalesce(extract(epoch from (?::timestamptz - min(case when status='PROCESSING' then claimed_at else created_at end))),0)
                from ostris.protocol_proof_outbox where status=?
                """, Double.class, java.sql.Timestamp.from(clock.instant()), status);
        return value == null ? 0 : Math.max(0, value);
    }
}
