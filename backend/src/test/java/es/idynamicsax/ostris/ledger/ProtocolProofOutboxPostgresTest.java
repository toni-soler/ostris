package es.idynamicsax.ostris.ledger;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.idynamicsax.ostris.config.OstrisFlywayConfig;
import es.idynamicsax.ostris.core.OstrisWireCodec;
import es.idynamicsax.ostris.proof.CommittedJournalTransaction;
import es.idynamicsax.ostris.proof.ProtocolEventProofBuilder;
import es.idynamicsax.ostris.proof.ProtocolEventProofV1;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProtocolProofOutboxPostgresTest {
    private static final MutableClock CLOCK = new MutableClock(Instant.now());
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("ostris-proof").withUsername("postgres").withPassword("test")
            .withInitScript("postgres-init.sql");

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({OstrisFlywayConfig.class, ProtocolProofOutboxStore.class, ProtocolProofOutboxObservability.class})
    static class TestApp {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean java.time.Clock clock() { return CLOCK; }
        @Bean OstrisLedgerDeliveryProperties properties() {
            return new OstrisLedgerDeliveryProperties(true, null, null, null, null, null,
                    java.time.Duration.ofSeconds(1), java.time.Duration.ofSeconds(1), java.time.Duration.ofSeconds(1),
                    java.time.Duration.ofSeconds(5), 10, java.time.Duration.ofMinutes(2),
                    java.time.Duration.ofSeconds(10), java.time.Duration.ofMinutes(15));
        }
    }

    private ConfigurableApplicationContext context;

    @BeforeAll void start() { restart(); }
    @AfterAll void stop() { if (context != null) context.close(); }

    @Test
    void atomicallyClaimsAndReproducesExactlyTheSameProofAfterRealRestart() throws Exception {
        JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
        ObjectMapper mapper = context.getBean(ObjectMapper.class);
        ProtocolEventProofBuilder builder = new ProtocolEventProofBuilder(mapper, new OstrisWireCodec());
        Fixture fixture = fixture(mapper);
        seed(jdbc, fixture);
        ProtocolEventProofV1 before = builder.build(fixture.journal());

        ProtocolProofOutboxStore store = context.getBean(ProtocolProofOutboxStore.class);
        var firstClaim = store.claimNext().orElseThrow();
        assertEquals("PROCESSING", jdbc.queryForObject(
                "select status from ostris.protocol_proof_outbox where id=?", String.class, firstClaim.id()));
        assertNotNull(firstClaim.claimToken());
        assertTrue(store.claimNext().isEmpty(), "SKIP LOCKED/PROCESSING claim must prevent duplicate delivery");
        assertEquals(fixture.journal(), store.loadJournal(firstClaim));

        context.close();
        context = null;
        CLOCK.advance(Duration.ofMinutes(3));
        restart();
        jdbc = context.getBean(JdbcTemplate.class);
        store = context.getBean(ProtocolProofOutboxStore.class);
        var recoveredClaim = store.claimNext().orElseThrow();
        assertNotEquals(firstClaim.claimToken(), recoveredClaim.claimToken());
        assertEquals(2, recoveredClaim.attemptCount());
        assertTrue(store.anchored(recoveredClaim, UUID.randomUUID()));
        assertFalse(store.failed(firstClaim, false, "LATE_STALE_FAILURE"));
        assertEquals("ANCHORED", jdbc.queryForObject(
                "select status from ostris.protocol_proof_outbox where id=?", String.class, firstClaim.id()));
        assertTrue(store.claimNext().isEmpty(), "ANCHORED is terminal and cannot be reclaimed");
        builder = new ProtocolEventProofBuilder(context.getBean(ObjectMapper.class), new OstrisWireCodec());
        CommittedJournalTransaction reloaded = jdbc.queryForObject("""
                select tenant_id,community_id,unit_id,id,community_sequence,status,canonical_json,protocol_digest
                from ostris.journal_transaction where id=?
                """, (rs, row) -> new CommittedJournalTransaction(
                rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class),
                rs.getObject(4, UUID.class), rs.getLong(5), rs.getString(6), rs.getString(7), rs.getString(8)),
                fixture.journal().transactionId());
        ProtocolEventProofV1 after = builder.build(reloaded);
        assertEquals(before.canonicalJson(), after.canonicalJson());
        assertEquals(before.transactionDigest(), after.transactionDigest());
        assertEquals(before.proofDigest(), after.proofDigest());
        assertEquals(before.externalReference(), after.externalReference());
        assertEquals(before.payload().communitySequence(), after.payload().communitySequence());
    }

    @Test
    void retryScheduleSurvivesPollingAndEventuallyRecoversWithoutChangingJournal() throws Exception {
        JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
        Fixture fixture = fixture(context.getBean(ObjectMapper.class));
        seed(jdbc, fixture);
        ProtocolProofOutboxStore store = context.getBean(ProtocolProofOutboxStore.class);
        var first = store.claimNext().orElseThrow();
        String journalBefore = jdbc.queryForObject(
                "select canonical_json from ostris.journal_transaction where id=?", String.class,
                fixture.journal().transactionId());
        assertTrue(store.failed(first, false, "CONNECTION_REFUSED"));
        var observability = context.getBean(ProtocolProofOutboxObservability.class);
        assertEquals("UP", observability.health().getStatus().getCode());
        assertEquals("DEGRADED", observability.health().getDetails().get("ledgerDelivery"));
        var registry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        observability.bindTo(registry);
        assertEquals(1.0, registry.get("ostris.ledger.outbox.rows")
                .tag("status", "FAILED_RETRYABLE").gauge().value());
        assertTrue(store.claimNext().isEmpty());
        assertEquals(1, jdbc.queryForObject(
                "select attempt_count from ostris.protocol_proof_outbox where id=?", Integer.class, first.id()));
        CLOCK.advance(Duration.ofSeconds(10));
        var retry = store.claimNext().orElseThrow();
        assertEquals(2, retry.attemptCount());
        assertEquals(first.externalReference(), retry.externalReference());
        assertEquals(first.proofDigest(), retry.proofDigest());
        assertTrue(store.anchored(retry, UUID.randomUUID()));
        assertEquals("AVAILABLE", observability.health().getDetails().get("ledgerDelivery"));
        assertEquals(journalBefore, jdbc.queryForObject(
                "select canonical_json from ostris.journal_transaction where id=?", String.class,
                fixture.journal().transactionId()));
        assertEquals("COMMITTED", jdbc.queryForObject(
                "select status from ostris.journal_transaction where id=?", String.class,
                fixture.journal().transactionId()));
    }

    @Test
    void independentWorkerGraphsProcessManyRowsWithoutLossOrDeadlock() throws Exception {
        JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
        ObjectMapper mapper = context.getBean(ObjectMapper.class);
        for (int i = 0; i < 30; i++) seed(jdbc, fixture(mapper));
        var transactionManager = context.getBean(PlatformTransactionManager.class);
        var properties = context.getBean(OstrisLedgerDeliveryProperties.class);
        var first = new ProtocolProofOutboxStore(jdbc, transactionManager, CLOCK, properties);
        var second = new ProtocolProofOutboxStore(jdbc, transactionManager, CLOCK, properties);
        var claimed = ConcurrentHashMap.<UUID>newKeySet();
        var pool = Executors.newFixedThreadPool(2);
        var tasks = java.util.List.of(first, second).stream().map(store -> (java.util.concurrent.Callable<Integer>) () -> {
            int count = 0;
            while (true) {
                var next = store.claimNext();
                if (next.isEmpty()) return count;
                assertTrue(claimed.add(next.get().id()), "one active claim per row");
                assertTrue(store.anchored(next.get(), UUID.randomUUID()));
                count++;
            }
        }).toList();
        var results = pool.invokeAll(tasks);
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        assertEquals(30, results.stream().mapToInt(result -> {
            try { return result.get(); } catch (Exception error) { throw new RuntimeException(error); }
        }).sum());
        assertEquals(30, claimed.size());
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from ostris.protocol_proof_outbox where status='PROCESSING'", Integer.class));
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from ostris.protocol_proof_outbox where status<>'ANCHORED'", Integer.class));
        assertEquals(0, jdbc.queryForObject("""
                select count(*) from (
                  select tenant_id,external_reference,count(*) from ostris.protocol_proof_outbox
                  group by tenant_id,external_reference having count(*) > 1
                ) duplicates
                """, Integer.class));
    }

    private void restart() {
        context = new SpringApplicationBuilder(TestApp.class).web(WebApplicationType.NONE).run(
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--spring.jpa.hibernate.ddl-auto=none", "--spring.flyway.enabled=false");
    }

    private static void seed(JdbcTemplate jdbc, Fixture fixture) {
        var j = fixture.journal();
        jdbc.update("insert into idax_core.tenant(tenant_id) values(?)", j.tenantId());
        jdbc.update("insert into ostris.community(id,tenant_id,name,next_sequence) values(?,?,?,?)",
                j.communityId(), j.tenantId(), "Proof", j.communitySequence() + 1);
        jdbc.update("insert into ostris.unit_of_account(id,tenant_id,community_id,code,scale) values(?,?,?,?,2)",
                j.unitId(), j.tenantId(), j.communityId(), "PRF");
        jdbc.update("""
                insert into ostris.journal_transaction(id,tenant_id,community_id,unit_id,community_sequence,purpose,
                    references_json,policy_snapshot_json,canonical_json,protocol_digest,status)
                values(?,?,?,?,?,'EXCHANGE','{}'::jsonb,'{}'::jsonb,?,?,'COMMITTED')
                """, j.transactionId(), j.tenantId(), j.communityId(), j.unitId(), j.communitySequence(),
                j.canonicalTransactionJson(), j.persistedProtocolDigest());
        jdbc.update("""
                insert into ostris.protocol_proof_outbox(id,tenant_id,community_id,transaction_id,protocol_digest,status,
                    proof_digest,external_reference,canonicalization_profile)
                values(?,?,?,?,?,'PENDING',?,?,?)
                """, UUID.randomUUID(), j.tenantId(), j.communityId(), j.transactionId(), j.persistedProtocolDigest(),
                fixture.proof().proofDigest(), fixture.proof().externalReference(), fixture.proof().canonicalizationProfile());
    }

    private static Fixture fixture(ObjectMapper mapper) throws Exception {
        JsonNode proofVectors = mapper.readTree(Files.readString(Path.of("..", "docs", "specification", "test-vectors", "protocol-event-proof-v1.json")));
        JsonNode payload = proofVectors.path("valid").get(0).path("payload");
        String protocolDigest = proofVectors.path("sourceEvent").path("protocolDigest").asText();
        JsonNode core = mapper.readTree(Files.readString(Path.of("..", "docs", "specification", "test-vectors", "core-v0.1.json")));
        String canonical = null;
        for (JsonNode candidate : core.path("canonicalization")) {
            if (protocolDigest.equals(candidate.path("expectedSha256").asText())) canonical = candidate.path("expectedCanonical").asText();
        }
        assertNotNull(canonical);
        var journal = new CommittedJournalTransaction(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), payload.path("communitySequence").asLong(),
                "COMMITTED", canonical, protocolDigest);
        return new Fixture(journal, new ProtocolEventProofBuilder(mapper, new OstrisWireCodec()).build(journal));
    }

    private record Fixture(CommittedJournalTransaction journal, ProtocolEventProofV1 proof) {}

    private static final class MutableClock extends Clock {
        private Instant instant;
        private MutableClock(Instant instant) { this.instant = instant; }
        void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
