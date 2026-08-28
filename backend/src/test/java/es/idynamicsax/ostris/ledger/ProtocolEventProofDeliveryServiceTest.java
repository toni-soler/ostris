package es.idynamicsax.ostris.ledger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.idynamicsax.idax.service.auth.ServiceTokenProvider;
import es.idynamicsax.idax.service.auth.ServiceTokenResponse;
import es.idynamicsax.ostris.proof.CommittedJournalTransaction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

class ProtocolEventProofDeliveryServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ProtocolProofOutboxStore outbox = mock(ProtocolProofOutboxStore.class);
    private final ServiceTokenProvider tokens = mock(ServiceTokenProvider.class);
    private final LedgerProofClient ledger = mock(LedgerProofClient.class);
    private final ProtocolProofDeliveryMetrics metrics = new ProtocolProofDeliveryMetrics(
            new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    private final OstrisLedgerDeliveryProperties enabled = properties(true);

    @Test
    void anchorsOnlyAfterValidatedMatchUsingTenantScopedLeastPrivilegeToken() throws Exception {
        Fixture fixture = fixture();
        when(outbox.claimNext()).thenReturn(Optional.of(fixture.claim()));
        when(outbox.loadJournal(fixture.claim())).thenReturn(fixture.journal());
        when(tokens.token(fixture.claim().tenantId(), "idax-ledger", ProtocolEventProofDeliveryService.PERMISSIONS))
                .thenReturn(new ServiceTokenResponse("token", "Bearer", Instant.now().plusSeconds(60), 60));
        UUID proofId = UUID.randomUUID();
        when(outbox.proofCreated(fixture.claim(), proofId)).thenReturn(true);
        when(ledger.create(eq("token"), eq(fixture.claim().externalReference()), any()))
                .thenReturn(new LedgerProofClient.CreatedProof(proofId, fixture.claim().externalReference(),
                        fixture.claim().proofDigest(), fixture.claim().canonicalizationProfile(), "VALIDATED",
                        new LedgerProofClient.CreatedProof.Submission("VALIDATED", 1, null, null, null)));
        when(ledger.verify(eq("token"), eq(proofId), any())).thenReturn(match());

        assertTrue(service(enabled).deliverNext());

        verify(outbox).proofCreated(fixture.claim(), proofId);
        verify(outbox).anchored(fixture.claim(), proofId);
        verify(outbox, never()).failed(any(), anyBoolean(), anyString());
        verify(tokens).token(fixture.claim().tenantId(), "idax-ledger",
                java.util.Set.of("LEDGER_PROOF_CREATE", "LEDGER_READ", "LEDGER_PROOF_VERIFY"));
    }

    @Test
    void fiveHundredIsRetryableAndNeverAnchors() throws Exception {
        Fixture fixture = fixture();
        stubClaim(fixture);
        when(ledger.create(anyString(), anyString(), any())).thenThrow(
                HttpServerErrorException.create(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                        "failure", null, null, null));
        assertTrue(service(enabled).deliverNext());
        verify(outbox).failed(fixture.claim(), false, "LEDGER_HTTP_500");
        verify(outbox, never()).anchored(any(), any());
    }

    @Test
    void validationMismatchIsPermanentAndNeverAnchors() throws Exception {
        Fixture fixture = fixture();
        stubClaim(fixture);
        UUID proofId = UUID.randomUUID();
        when(ledger.create(anyString(), anyString(), any())).thenReturn(new LedgerProofClient.CreatedProof(
                proofId, fixture.claim().externalReference(), fixture.claim().proofDigest(),
                fixture.claim().canonicalizationProfile(), "VALIDATED",
                new LedgerProofClient.CreatedProof.Submission("VALIDATED", 1, null, null, null)));
        when(ledger.verify(anyString(), eq(proofId), any())).thenReturn(new LedgerProofClient.Verification(
                new LedgerProofClient.Verification.Integrity("MISMATCH", null, null),
                new LedgerProofClient.Verification.Ledger("VALIDATED_MATCH", null, null, null, null)));
        assertTrue(service(enabled).deliverNext());
        verify(outbox).failed(fixture.claim(), true, "LEDGER_INTEGRITY_MISMATCH");
        verify(outbox, never()).anchored(any(), any());
    }

    @Test
    void tamperedCachedProofFailsClosedBeforeTokenOrHttp() throws Exception {
        Fixture fixture = fixture();
        var tampered = new ProtocolProofOutboxStore.ClaimedProof(fixture.claim().id(), fixture.claim().tenantId(),
                fixture.claim().communityId(), fixture.claim().transactionId(), fixture.claim().protocolDigest(),
                "0".repeat(64), fixture.claim().externalReference(), fixture.claim().canonicalizationProfile(),
                fixture.claim().claimToken(), null, 1);
        when(outbox.claimNext()).thenReturn(Optional.of(tampered));
        when(outbox.loadJournal(tampered)).thenReturn(fixture.journal());
        assertTrue(service(enabled).deliverNext());
        verify(outbox).failed(eq(tampered), eq(true), contains("proofDigest"));
        verifyNoInteractions(tokens, ledger);
    }

    @Test
    void disabledModeMakesNoTokenOrHttpCallAndPreservesTraceAsDisabled() {
        assertFalse(service(properties(false)).deliverNext());
        verify(outbox).disabledPending();
        verifyNoInteractions(tokens, ledger);
    }

    @Test
    void ambiguousCreateRetriesTheExactSameIdentityAndBodyThenAnchors() throws Exception {
        Fixture fixture = fixture();
        var retryClaim = claim(fixture, null, 2);
        when(outbox.claimNext()).thenReturn(Optional.of(fixture.claim()), Optional.of(retryClaim));
        when(outbox.loadJournal(any())).thenReturn(fixture.journal());
        when(tokens.token(any(), anyString(), any())).thenReturn(
                new ServiceTokenResponse("token", "Bearer", Instant.now().plusSeconds(60), 60));
        UUID proofId = UUID.randomUUID();
        when(ledger.create(anyString(), anyString(), any()))
                .thenThrow(new ResourceAccessException("response dropped"))
                .thenReturn(validatedProof(fixture, proofId));
        when(outbox.proofCreated(retryClaim, proofId)).thenReturn(true);
        when(ledger.verify(anyString(), eq(proofId), any())).thenReturn(match());

        assertTrue(service(enabled).deliverNext());
        assertTrue(service(enabled).deliverNext());

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<LedgerProofClient.LedgerProofRequest> bodies = ArgumentCaptor.forClass(LedgerProofClient.LedgerProofRequest.class);
        verify(ledger, times(2)).create(eq("token"), keys.capture(), bodies.capture());
        assertEquals(java.util.List.of(fixture.claim().externalReference(), fixture.claim().externalReference()), keys.getAllValues());
        assertEquals(bodies.getAllValues().get(0), bodies.getAllValues().get(1));
        verify(outbox).failed(fixture.claim(), false, "LEDGER_ResourceAccessException");
        verify(outbox).anchored(retryClaim, proofId);
    }

    @Test
    void knownProofReferenceSkipsCreateAndReverifiesAfterCrash() throws Exception {
        Fixture fixture = fixture();
        UUID proofId = UUID.randomUUID();
        var recovered = claim(fixture, proofId.toString(), 2);
        when(outbox.claimNext()).thenReturn(Optional.of(recovered));
        when(outbox.loadJournal(recovered)).thenReturn(fixture.journal());
        when(tokens.token(any(), anyString(), any())).thenReturn(
                new ServiceTokenResponse("new-token", "Bearer", Instant.now().plusSeconds(60), 60));
        when(ledger.get("new-token", proofId)).thenReturn(validatedProof(fixture, proofId));
        when(ledger.verify(anyString(), eq(proofId), any())).thenReturn(match());

        assertTrue(service(enabled).deliverNext());

        verify(ledger, never()).create(anyString(), anyString(), any());
        verify(outbox).anchored(recovered, proofId);
    }

    @ParameterizedTest
    @CsvSource({"400,true", "401,true", "403,true", "404,true", "409,true", "429,false", "500,false", "503,false"})
    void classifiesHttpErrors(int status, boolean permanent) throws Exception {
        Fixture fixture = fixture();
        stubClaim(fixture);
        var httpStatus = org.springframework.http.HttpStatusCode.valueOf(status);
        when(ledger.create(anyString(), anyString(), any())).thenThrow(
                HttpClientErrorException.create(httpStatus, "failure", null, null, null));
        service(enabled).deliverNext();
        if (status == 429) verify(outbox).failed(eq(fixture.claim()), eq(false), eq("LEDGER_HTTP_429"), any());
        else verify(outbox).failed(fixture.claim(), permanent,
                status == 409 ? "IDEMPOTENCY_CONFLICT" : "LEDGER_HTTP_" + status);
        verify(outbox, never()).anchored(any(), any());
    }

    @ParameterizedTest
    @CsvSource({"PENDING,false", "SIGNED,false", "SUBMITTED,false", "FAILED_RETRYABLE,false", "FAILED_PERMANENT,true", "ATTEMPT_EXPIRED,true"})
    void mapsLedgerProofStates(String ledgerStatus, boolean permanent) throws Exception {
        Fixture fixture = fixture();
        stubClaim(fixture);
        UUID proofId = UUID.randomUUID();
        when(ledger.create(anyString(), anyString(), any())).thenReturn(new LedgerProofClient.CreatedProof(
                proofId, fixture.claim().externalReference(), fixture.claim().proofDigest(),
                fixture.claim().canonicalizationProfile(), ledgerStatus,
                new LedgerProofClient.CreatedProof.Submission(ledgerStatus, 1, null, null, null)));
        service(enabled).deliverNext();
        verify(outbox).failed(fixture.claim(), permanent, "LEDGER_" + ledgerStatus);
        verify(ledger, never()).verify(anyString(), any(), any());
    }

    private void stubClaim(Fixture fixture) {
        when(outbox.claimNext()).thenReturn(Optional.of(fixture.claim()));
        when(outbox.loadJournal(fixture.claim())).thenReturn(fixture.journal());
        when(tokens.token(any(), anyString(), any())).thenReturn(
                new ServiceTokenResponse("token", "Bearer", Instant.now().plusSeconds(60), 60));
        when(outbox.proofCreated(eq(fixture.claim()), any())).thenReturn(true);
    }

    private ProtocolEventProofDeliveryService service(OstrisLedgerDeliveryProperties properties) {
        return new ProtocolEventProofDeliveryService(outbox, mapper, tokens, ledger, properties, metrics,
                java.time.Clock.systemUTC());
    }

    private ProtocolProofOutboxStore.ClaimedProof claim(Fixture fixture, String proofReference, int attempt) {
        var c = fixture.claim();
        return new ProtocolProofOutboxStore.ClaimedProof(c.id(), c.tenantId(), c.communityId(), c.transactionId(),
                c.protocolDigest(), c.proofDigest(), c.externalReference(), c.canonicalizationProfile(),
                UUID.randomUUID(), proofReference, attempt);
    }

    private LedgerProofClient.CreatedProof validatedProof(Fixture fixture, UUID proofId) {
        return new LedgerProofClient.CreatedProof(proofId, fixture.claim().externalReference(),
                fixture.claim().proofDigest(), fixture.claim().canonicalizationProfile(), "VALIDATED",
                new LedgerProofClient.CreatedProof.Submission("VALIDATED", 1, "hash", 1L, "ledger"));
    }

    private Fixture fixture() throws Exception {
        JsonNode vectors = mapper.readTree(Files.readString(Path.of("..", "docs", "specification", "test-vectors", "protocol-event-proof-v1.json")));
        JsonNode source = vectors.path("sourceEvent");
        JsonNode proof = vectors.path("valid").get(0);
        JsonNode payload = proof.path("payload");
        JsonNode core = mapper.readTree(Files.readString(Path.of("..", "docs", "specification", "test-vectors", "core-v0.1.json")));
        String canonical = null;
        for (JsonNode candidate : core.path("canonicalization")) {
            if (source.path("protocolDigest").asText().equals(candidate.path("expectedSha256").asText())) {
                canonical = candidate.path("expectedCanonical").asText();
            }
        }
        assertNotNull(canonical);
        UUID tenant = UUID.randomUUID();
        UUID community = UUID.fromString(payload.path("communityId").asText());
        UUID transaction = UUID.fromString(payload.path("transactionId").asText());
        var journal = new CommittedJournalTransaction(tenant, community,
                UUID.fromString(payload.path("unitId").asText()), transaction,
                payload.path("communitySequence").asLong(), "COMMITTED", canonical,
                source.path("protocolDigest").asText());
        var claim = new ProtocolProofOutboxStore.ClaimedProof(UUID.randomUUID(), tenant, community, transaction,
                source.path("protocolDigest").asText(), proof.path("expectedProofDigest").asText(),
                "ostris:v1:" + vectors.path("externalReferences").get(0).path("expectedExternalReference").asText().substring(10),
                "EXTERNAL:OSTRIS-PROTOCOL-EVENT-PROOF-V1", UUID.randomUUID(), null, 1);
        return new Fixture(claim, journal);
    }

    private static LedgerProofClient.Verification match() {
        return new LedgerProofClient.Verification(
                new LedgerProofClient.Verification.Integrity("MATCH", null, null),
                new LedgerProofClient.Verification.Ledger("VALIDATED_MATCH", null, null, 1L, "hash"));
    }

    private static OstrisLedgerDeliveryProperties properties(boolean enabled) {
        return new OstrisLedgerDeliveryProperties(enabled, null, null, null, null, null,
                Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1),
                Duration.ofSeconds(5), 10, Duration.ofMinutes(2), Duration.ofSeconds(10), Duration.ofMinutes(15));
    }

    private record Fixture(ProtocolProofOutboxStore.ClaimedProof claim, CommittedJournalTransaction journal) {}
}
