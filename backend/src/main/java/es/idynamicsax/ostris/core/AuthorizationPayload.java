package es.idynamicsax.ostris.core;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AuthorizationPayload(
        String wireFormat,
        UUID unitId,
        UUID transactionId,
        Map<String, String> references,
        TransactionPurpose purpose,
        String ostrisCoreVersion,
        List<TransactionEntry> entries,
        UUID communityId,
        String contractualMetadataDigest) {
    public AuthorizationPayload {
        if (!"OSTRIS-CORE-JCS-1".equals(wireFormat) || !"0.1".equals(ostrisCoreVersion)) throw new ProtocolException("UNSUPPORTED_WIRE", "Unsupported Core/wire version");
        if (communityId == null || unitId == null || transactionId == null || purpose == null || entries == null || entries.size() < 2 || references == null) throw new ProtocolException("INVALID_AUTHORIZATION_PAYLOAD", "Missing authorization intent");
        if (contractualMetadataDigest != null && !contractualMetadataDigest.matches("[0-9a-f]{64}")) throw new ProtocolException("INVALID_CONTRACT_DIGEST", "Contract digest must be lowercase SHA-256");
    }
}
