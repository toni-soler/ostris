package es.idynamicsax.ostris.proof;

/** Reproducible proof material; Ledger linkage is deliberately absent. */
public record ProtocolEventProofV1(
        ProtocolEventProofPayloadV1 payload,
        String canonicalJson,
        String transactionDigest,
        String proofDigest,
        String externalReference,
        String canonicalizationProfile) {
}
