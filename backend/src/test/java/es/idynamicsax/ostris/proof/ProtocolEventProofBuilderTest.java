package es.idynamicsax.ostris.proof;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.idynamicsax.ostris.core.OstrisWireCodec;
import es.idynamicsax.ostris.core.ProtocolException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProtocolEventProofBuilderTest {
    private static final String EXPECTED_SUITE = "c06a044828c466506611195299d2f49a8d6f5db97ce29922dcb7e435525473c9";
    private static final String NORMATIVE_TRANSACTION_DIGEST = "8bafeb24c4b300ab5139bf1a1190c1d45bc5f295342b7ffd3b46c0485eae93a5";
    private final ObjectMapper mapper = new ObjectMapper();
    private final OstrisWireCodec wire = new OstrisWireCodec();
    private final ProtocolEventProofBuilder builder = new ProtocolEventProofBuilder(mapper, wire);

    @Test
    void matchesNormativeCanonicalBytesDigestsAndExternalReferences() throws Exception {
        JsonNode root = vectors();
        assertEquals(EXPECTED_SUITE, root.path("expectedSuiteSha256").asText());
        JsonNode source = root.path("sourceEvent");
        for (JsonNode vector : root.path("valid")) {
            JsonNode payload = vector.path("payload");
            var proof = builder.build(source(payload, source.path("protocolDigest").asText(), "COMMITTED"));
            assertEquals(vector.path("expectedCanonical").asText(), proof.canonicalJson(), vector.path("name").asText());
            assertEquals(vector.path("expectedProofDigest").asText(), proof.proofDigest(), vector.path("name").asText());
            assertNotEquals(OstrisWireCodec.sha256(proof.canonicalJson().getBytes(StandardCharsets.UTF_8)), proof.proofDigest());
            assertEquals(vector.path("expectedProofDigest").asText(), OstrisWireCodec.sha256(builder.proofPreimage(proof)));
        }
        for (JsonNode vector : root.path("externalReferences")) {
            JsonNode payload = root.path("valid").get(0).path("payload").deepCopy();
            ((com.fasterxml.jackson.databind.node.ObjectNode) payload)
                    .put("communityId", vector.path("communityId").asText())
                    .put("transactionId", vector.path("transactionId").asText());
            var proof = builder.build(source(payload, source.path("protocolDigest").asText(), "COMMITTED"));
            assertEquals(vector.path("expectedExternalReference").asText(), proof.externalReference());
            String naive = "ostris:v1:" + sha256(vector.path("communityId").asText() + vector.path("transactionId").asText());
            assertNotEquals(naive, proof.externalReference());
            assertEquals(74, proof.externalReference().length());
            assertFalse(proof.externalReference().contains(vector.path("transactionId").asText()));
        }
    }

    @Test
    void failsClosedForUncommittedOrMismatchedJournalState() throws Exception {
        JsonNode root = vectors();
        JsonNode payload = root.path("valid").get(0).path("payload");
        String digest = root.path("sourceEvent").path("protocolDigest").asText();
        assertEquals("PROOF_REQUIRES_COMMITTED", assertThrows(ProtocolException.class,
                () -> builder.build(source(payload, digest, "PROPOSED"))).code());
        assertEquals("PROTOCOL_DIGEST_MISMATCH", assertThrows(ProtocolException.class,
                () -> builder.build(source(payload, "0000000000000000000000000000000000000000000000000000000000000000", "COMMITTED"))).code());
    }

    private CommittedJournalTransaction source(JsonNode payload, String digest, String status) throws Exception {
        String transactionIntent = Files.readString(Path.of("..", "docs", "specification", "test-vectors", "core-v0.1.json"));
        JsonNode core = mapper.readTree(transactionIntent);
        JsonNode canonicalCase = null;
        for (JsonNode candidate : core.path("canonicalization")) {
            if (candidate.path("expectedSha256").asText().equals(NORMATIVE_TRANSACTION_DIGEST)) canonicalCase = candidate;
        }
        assertNotNull(canonicalCase, "ProtocolEventProof source digest must resolve to a normative transaction intent");
        return new CommittedJournalTransaction(
                UUID.randomUUID(),
                UUID.fromString(payload.path("communityId").asText()),
                UUID.fromString(payload.path("unitId").asText()),
                UUID.fromString(payload.path("transactionId").asText()),
                Long.parseLong(payload.path("communitySequence").asText()),
                status,
                canonicalCase.path("expectedCanonical").asText(),
                digest);
    }

    private JsonNode vectors() throws Exception {
        return mapper.readTree(Files.readString(Path.of("..", "docs", "specification", "test-vectors", "protocol-event-proof-v1.json")));
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
