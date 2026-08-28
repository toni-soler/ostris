# ADR-015 — Domain-separated committed protocol event proof

Status: Accepted — 2026-08-26, normative human decision

## Context

The existing `protocolDigest` commits to canonical transaction intent. It does not bind the event to `COMMITTED` or to its normative `CommunitySequence`. IDAX Ledger 0.1.0 can anchor a caller-supplied SHA-256 digest without receiving economic content, but osTRIS previously did not define the exact committed-event bytes.

## Decision

Define the closed eight-field `ProtocolEventProofPayload v1`. Its `transactionDigest` equals the freshly reconstructed and verified transaction `protocolDigest`; its sequence and identifiers equal immutable journal state; its state is exactly `COMMITTED`.

The exact preimage is:

```text
UTF8("OSTRIS:PROTOCOL:EVENT:PROOF:V1") || 0x00 || JCS_UTF8(payload)
```

The external Ledger profile is `EXTERNAL:OSTRIS-PROTOCOL-EVENT-PROOF-V1`. The off-ledger external reference is independently domain-separated and deterministically derived from communityId and transactionId. The conformance-suite digest, Ledger IDs and provider metadata are not Proof fields.

## Consequences

Proof construction is reproducible after restart and binds normative ordering without duplicating entries or leaking economic/private data. Outbox delivery is at-least-once and fail-closed against reconstructed journal state. Ledger failure never changes economic truth. osTRIS marks `ANCHORED` only after `VALIDATED_MATCH`.

