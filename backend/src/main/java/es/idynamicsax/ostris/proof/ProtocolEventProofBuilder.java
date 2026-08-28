package es.idynamicsax.ostris.proof;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import es.idynamicsax.ostris.core.OstrisWireCodec;
import es.idynamicsax.ostris.core.ProtocolException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** Pure construction and integrity verification for ProtocolEventProof v1. */
public final class ProtocolEventProofBuilder {
    public static final String CORE_VERSION = "0.1";
    public static final String WIRE_FORMAT = "OSTRIS-CORE-JCS-1";
    public static final String COMMIT_STATE = "COMMITTED";
    public static final String PROOF_DOMAIN = "OSTRIS:PROTOCOL:EVENT:PROOF:V1";
    public static final String EXTERNAL_REFERENCE_DOMAIN = "OSTRIS:LEDGER:EXTERNAL-REF:V1";
    public static final String PROFILE = "EXTERNAL:OSTRIS-PROTOCOL-EVENT-PROOF-V1";
    private static final Pattern LOWER_SHA256 = Pattern.compile("^[0-9a-f]{64}$");

    private final ObjectMapper mapper;
    private final OstrisWireCodec wire;

    public ProtocolEventProofBuilder(ObjectMapper mapper, OstrisWireCodec wire) {
        this.mapper = mapper;
        this.wire = wire;
    }

    public ProtocolEventProofV1 build(CommittedJournalTransaction source) {
        if (!COMMIT_STATE.equals(source.status())) {
            throw new ProtocolException("PROOF_REQUIRES_COMMITTED", "Protocol event proofs require COMMITTED journal state");
        }
        if (source.communitySequence() < 0) {
            throw new ProtocolException("INVALID_COMMUNITY_SEQUENCE", "CommunitySequence must be non-negative");
        }
        if (!LOWER_SHA256.matcher(source.persistedProtocolDigest()).matches()) {
            throw new ProtocolException("INVALID_PROTOCOL_DIGEST", "Persisted protocolDigest must be lowercase SHA-256");
        }

        byte[] reconstructedCanonical = wire.canonicalize(wire.parse(source.canonicalTransactionJson()));
        String reconstructedDigest = OstrisWireCodec.sha256(reconstructedCanonical);
        if (!reconstructedDigest.equals(source.persistedProtocolDigest())) {
            throw new ProtocolException("PROTOCOL_DIGEST_MISMATCH", "Reconstructed transaction intent does not match persisted protocolDigest");
        }

        ProtocolEventProofPayloadV1 payload = new ProtocolEventProofPayloadV1(
                CORE_VERSION,
                WIRE_FORMAT,
                source.communityId().toString(),
                source.unitId().toString(),
                source.transactionId().toString(),
                reconstructedDigest,
                Long.toString(source.communitySequence()),
                COMMIT_STATE);
        ObjectNode node = mapper.createObjectNode();
        node.put("ostrisCoreVersion", payload.ostrisCoreVersion());
        node.put("wireFormat", payload.wireFormat());
        node.put("communityId", payload.communityId());
        node.put("unitId", payload.unitId());
        node.put("transactionId", payload.transactionId());
        node.put("transactionDigest", payload.transactionDigest());
        node.put("communitySequence", payload.communitySequence());
        node.put("commitState", payload.commitState());
        byte[] canonical = wire.canonicalize(node);
        String proofDigest = OstrisWireCodec.sha256(domainSeparated(PROOF_DOMAIN, canonical));
        String externalReference = "ostris:v1:" + OstrisWireCodec.sha256(externalReferenceBytes(source));
        return new ProtocolEventProofV1(payload, new String(canonical, StandardCharsets.UTF_8),
                reconstructedDigest, proofDigest, externalReference, PROFILE);
    }

    public byte[] proofPreimage(ProtocolEventProofV1 proof) {
        return domainSeparated(PROOF_DOMAIN, proof.canonicalJson().getBytes(StandardCharsets.UTF_8));
    }

    private byte[] externalReferenceBytes(CommittedJournalTransaction source) {
        byte[] domain = EXTERNAL_REFERENCE_DOMAIN.getBytes(StandardCharsets.UTF_8);
        byte[] community = source.communityId().toString().getBytes(StandardCharsets.UTF_8);
        byte[] transaction = source.transactionId().toString().getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[domain.length + community.length + transaction.length + 2];
        int offset = copy(domain, bytes, 0);
        bytes[offset++] = 0;
        offset = copy(community, bytes, offset);
        bytes[offset++] = 0;
        copy(transaction, bytes, offset);
        return bytes;
    }

    private static byte[] domainSeparated(String domainName, byte[] value) {
        byte[] domain = domainName.getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[domain.length + value.length + 1];
        int offset = copy(domain, bytes, 0);
        bytes[offset] = 0;
        copy(value, bytes, offset + 1);
        return bytes;
    }

    private static int copy(byte[] source, byte[] target, int offset) {
        System.arraycopy(source, 0, target, offset, source.length);
        return offset + source.length;
    }
}
