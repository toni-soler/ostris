# Formal Invariants — Core v0.1

An implementation claiming Core v0.1 conformance **MUST** preserve:

| ID     | Invariant                                                                                                                                               |
| ------ | ------------------------------------------------------------------------------------------------------------------------------------------------------- |
| INV-01 | Every committed transaction has at least two entries in one community/unit and their exact sum is zero.                                                 |
| INV-02 | Every economic balance mutation is an atomic journal commit; no direct balance mutation is permitted.                                                   |
| INV-03 | Balance is reconstructible from committed entries; a divergent projection is non-authoritative.                                                         |
| INV-04 | In a closed community/unit, the sum of all balances—including governed special accounts—is zero.                                                        |
| INV-05 | A committed transaction, entries and decision snapshot are immutable and remain COMMITTED forever.                                                      |
| INV-06 | Reversal, settlement, restitution, write-off, penalty and loss offset are new transactions; none erases history.                                        |
| INV-07 | Every affected account is authorized at commit by its effective AccountControlPolicy or, only where purpose and effective policy permit, by a valid transaction-specific GovernedAuthorization explicitly covering it. |
| INV-08 | IdentityPolicy, CreditPolicy and RiskPolicy checks precede a voluntary-exposure commit.                                                                 |
| INV-09 | Every commit references immutable policy versions and sufficient reproducible decision facts.                                                           |
| INV-10 | Same transaction ID/content is idempotent; different content with the same ID is conflict.                                                              |
| INV-11 | Floor change creates no entry, balance change or default.                                                                                               |
| INV-12 | Protocol/wire version, community, unit, accounts and closed TransactionPurpose are explicit and valid.                                                  |
| INV-13 | Passage of time alone never creates entries or changes balances.                                                                                        |
| INV-14 | Balance, enforced liability, exposure, risk, reputation, assurance and trust are distinct.                                                              |
| INV-15 | Dispute, signal, case or Finding never changes accounting without a separately authorized transaction.                                                  |
| INV-16 | Economic sanctions/write-offs are zero-sum journal transactions; non-economic measures never mutate balance.                                            |
| INV-17 | Special accounts cannot create units unilaterally and have explicit governance/ControlPolicy.                                                           |
| INV-18 | Social reset cannot silently reset CONFIRMED community-scoped risk continuity or accounting obligations.                                                |
| INV-19 | Preventive risk decisions establish no guilt; sanctions require process/finality and contestation.                                                      |
| INV-20 | Concurrent commits are observationally equivalent to CommunitySequence order preserving all invariants.                                                 |
| INV-21 | Proof/Anchor does not substitute for journal, authorization or policy evidence.                                                                         |
| INV-22 | PII/KYC documents and naïve hashes of public identifiers are not public protocol IDs/commitments.                                                       |
| INV-23 | Voluntary exposure cannot cross creditFloor; only governed RESTITUTION/PENALTY with permitted final basis, valid governance and matching effective typed SanctionPolicy flag may worsen balance below it, creating liability rather than credit. |
| INV-29 | Governed economic policy configs are closed typed objects evaluated at commit CommunitySequence; missing, unknown or wrongly typed fields fail closed and flags never enlarge the purpose/basis matrix. |
| INV-30 | Special-account purposes are fixed; REVERSAL may include a special account only by exact mechanical inheritance from the original. |
| INV-31 | WRITE_OFF v0.1 is full-only and leaves the defaulted account at exactly zero through CommunityLossAccount. |
| INV-32 | RESTITUTION paths are exclusive and proposal-bound: voluntary has no mandate basis/governance; imposed has governance and exactly one matching typed basis reference. |
| INV-24 | Amounts are exact canonical signed integer minor-unit strings; Unit scale `0..18` is immutable.                                                         |
| INV-25 | A v0.1 REVERSAL is the unique full entry-by-entry negation of exactly one known non-reversal original.                                                  |
| INV-26 | Authorization credentials, delegations, assurance and policies are evaluated at commit CommunitySequence; later revocation is non-retroactive.          |
| INV-27 | GovernedAuthorization cannot authorize EXCHANGE and requires a permitted final ResolutionBasis.                                                        |
| INV-28 | GovernedAuthorization covers only its explicitly listed accounts and exact signed transaction; unknown purpose/basis combinations fail closed.           |
| INV-29 | GovernedAuthorization is evaluated at commit; a governance credential revoked earlier is invalid and no governed resolution bypasses effective policy.    |
| INV-30 | Authorization thresholds count distinct eligible controller IDs; credentials, signatures, keys and evidence rows never constitute independent quorum units. |
| INV-31 | Each credential ID has one immutable controller binding; unbound, revoked, rebound or ambiguous bindings fail closed.                                  |
| INV-32 | One Ed25519 public key cannot represent eligible credentials for different controllers in the same Community.                                         |
| INV-33 | A ProtocolEventProof is derived only from immutable committed journal state and binds the exact transaction protocolDigest to its immutable CommunitySequence. |
| INV-34 | ProtocolEventProof v1 uses `OSTRIS:PROTOCOL:EVENT:PROOF:V1 || 0x00 || JCS(payload)` exactly; an undomain-separated digest is not a proofDigest. |
| INV-35 | Proof/outbox/Ledger metadata never changes journal truth, balances, CommunitySequence or COMMITTED state. |
| INV-36 | `ANCHORED` requires IDAX Ledger `VALIDATED_MATCH`; submission, persistence or validation status alone is insufficient. |
| INV-37 | Ledger outage, mismatch or terminal anchoring failure never invalidates an otherwise valid committed economic transaction. |
| INV-38 | Before delivery, implementations reconstruct and verify protocolDigest, proofDigest and externalReference from committed state; outbox mismatch fails closed. |
| INV-27 | CommunitySequence is unique/monotonic within one community and orders normative events; wall clock never decides precedence.                            |
| INV-28 | Culpability-based RESTITUTION/PENALTY requires a FINAL Finding; pre-finality prevention is non-accounting.                                              |
| INV-29 | The five Core risk metrics use normative inputs, sequence windows, purpose filters and integer rounding; thresholds are policy.                         |
| INV-30 | Unknown normative wire fields/types/algorithms fail closed; extensions cannot alter Core semantics/invariants.                                          |

For transaction `T`, exact balance `B` and non-positive floor `F`:

```text
Σ entry.amount(T) = 0
balance(account) = Σ committed entry.amount(account)
Σ balance(account in community/unit, including special accounts) = 0
availableNegativeExposure(B,F) = max(0, B - F)
enforcedLiability(B,F) = max(0, F - B)
```

FINAL-finding RESTITUTION/PENALTY may produce enforced liability without conferring voluntary credit. Third-party committed transactions are not retroactively invalidated when earlier proceeds are later found fraudulent.

## Configuration and wire invariants

Implementations reject impossible thresholds, unknown assurance/rule/metric/purpose/algorithm types, invalid time/sequence ranges, ambiguous active versions, missing special-account control and any configuration disabling Core invariants. Unsupported configuration is an error, not permissive fallback.

## Normative conformance

`test-vectors/core-v0.1.json` covers JCS/SHA-256/Ed25519, amount rejection, accounting, reversal uniqueness, credit/enforced liability, CommunitySequence ordering, identity continuity, five metrics and outcome precedence. All conforming implementations must reproduce its expected results.
