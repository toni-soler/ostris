package es.idynamicsax.ostris.proof;

import java.util.Objects;

/** Closed normative payload for a committed osTRIS protocol event proof. */
public record ProtocolEventProofPayloadV1(
        String ostrisCoreVersion,
        String wireFormat,
        String communityId,
        String unitId,
        String transactionId,
        String transactionDigest,
        String communitySequence,
        String commitState) {

    public ProtocolEventProofPayloadV1 {
        Objects.requireNonNull(ostrisCoreVersion);
        Objects.requireNonNull(wireFormat);
        Objects.requireNonNull(communityId);
        Objects.requireNonNull(unitId);
        Objects.requireNonNull(transactionId);
        Objects.requireNonNull(transactionDigest);
        Objects.requireNonNull(communitySequence);
        Objects.requireNonNull(commitState);
    }
}
