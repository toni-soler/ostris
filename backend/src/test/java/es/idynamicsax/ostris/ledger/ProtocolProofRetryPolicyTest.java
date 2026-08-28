package es.idynamicsax.ostris.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

class ProtocolProofRetryPolicyTest {
    @Test
    void exponentialBackoffIsDeterministicBoundedAndNeverAbandons() {
        var properties = new OstrisLedgerDeliveryProperties(true, null, null, null, null, null,
                Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(5), 10,
                Duration.ofMinutes(2), Duration.ofSeconds(10), Duration.ofMinutes(2));
        var store = new ProtocolProofOutboxStore(mock(JdbcTemplate.class),
                mock(PlatformTransactionManager.class), Clock.systemUTC(), properties);
        assertEquals(Duration.ofSeconds(10), store.retryDelay(1));
        assertEquals(Duration.ofSeconds(20), store.retryDelay(2));
        assertEquals(Duration.ofSeconds(40), store.retryDelay(3));
        assertEquals(Duration.ofSeconds(80), store.retryDelay(4));
        assertEquals(Duration.ofMinutes(2), store.retryDelay(5));
        assertEquals(Duration.ofMinutes(2), store.retryDelay(Integer.MAX_VALUE));
    }
}
