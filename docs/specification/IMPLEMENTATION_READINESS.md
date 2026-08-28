# Core v0.1 Implementation Readiness Assessment

## Verdict

**OSTRIS CORE v0.1 — READY FOR IMPLEMENTATION**

This means two independent teams can implement the Single Community accounting/risk core against the normative specification and test vectors. It does **not** mean a production implementation exists or authorize creation of backend/frontend/DB/API/IDAX/XRPL components in this phase.

## Reference implementation status

The separately versioned reference implementation has closed its bounded Core v0.1 compliance gate against normative suite SHA-256 `91bbeaf0bf97b7a20bdbb14f0414a995b5c4a844133b86fd422e6d05a0c828d6`.

**OSTRIS CORE v0.1 REFERENCE IMPLEMENTATION — CORE COMPLIANCE: PASS**

This records tested conformance of that implementation; it does not change the normative protocol, claim production readiness, replace legal/DPIA or external security review, or complete the pending IDAX Ledger adapter.

## Closed interoperability surface

- JCS RFC 8785 / I-JSON / UTF-8 canonical bytes;
- UUIDv7 lowercase canonical identifiers;
- exact signed integer minor-unit amounts, Unit scale `0..18`;
- SHA-256 and Ed25519 raw key/signature base64url-no-padding encoding;
- domain-separated AuthorizationPayload v1;
- domain-separated GovernanceAuthorizationPayload v1 and versioned CommunityResolutionAuthority;
- domain-separated ProtocolEventProofPayload v1 binding protocolDigest, COMMITTED state and immutable CommunitySequence;
- closed governed purpose/ResolutionBasis matrix, explicit covered-account substitution and commit-sequence threshold/revocation evaluation;
- stable controller-ID quorum, immutable controller/credential bindings, safe overlapping rotation and cross-controller public-key alias rejection;
- closed typed SanctionPolicy/DefaultPolicy governed-economic permissions and fixed special-account purpose matrix;
- exclusive voluntary/imposed RESTITUTION paths with typed, equality-bound finding/dispute references;
- seven TransactionPurpose values and purpose-specific references;
- unique full reversal, no partial reversal;
- voluntary floor versus FINAL enforced liability;
- Finding finality and third-party transaction finality;
- CommunitySequence policy/revocation/commit ordering;
- identity continuity CONFIRMED/CONTESTED/REJECTED semantics;
- five deterministic risk metric formulas and sequence window;
- four risk outcomes and fixed precedence;
- fail-closed extension/unknown semantics.

## Normative evidence

`test-vectors/core-v0.1.json` covers the economic Core. `test-vectors/protocol-event-proof-v1.json` independently covers exact committed-event JCS/proof digests, sequence binding, source mismatch, replay/mutation/domain separation and deterministic external references.

Python (`rfc8785`, `cryptography`) and Node (`canonicalize`, built-in `crypto`) independently produce suite digest:

```text
c06a044828c466506611195299d2f49a8d6f5db97ce29922dcb7e435525473c9
```

Both pass. Test private material is marked TEST ONLY.

## Remaining scope boundary

Community evidence thresholds, legal retention, deployment limits and future protocol features remain open but do not change frozen Core v0.1 computations. Production readiness is a separate future gate.

**READY FOR CORE IMPLEMENTATION**
