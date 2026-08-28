# Open Questions after Phase 3

No remaining question blocks implementation of Single Community Core v0.1 as specified and tested. These questions are policy, production or future-version matters and must not be answered differently inside a purported Core v0.1 wire implementation.

Governed resolution authorization is closed by ADR-012: the protocol fixes authority/version, threshold, signed payload, covered accounts and purpose/basis matrix; communities only choose concrete controllers and policy parameters.

## Community and production policy

1. What evidence threshold and human authority should a particular community require before issuing a Finding? Core fixes traceability/finality, not substantive legal proof.
2. How long may each category of private risk history be retained under the community's jurisdiction and purposes?
3. Which appeal timelines, interim restrictions and rehabilitation periods should a community configure?
4. How should unused CommunityPenaltyAccount balances be governed or distributed beyond LOSS_OFFSET?
5. Does a pilot need a CommunityReserveAccount or insurance mechanism? Neither is Core v0.1.
6. Which provider assurance profiles are accepted for each capability, and how is provider compromise handled operationally?
7. What deployment limits below the protocol caps should apply to amount digits, transaction size, entry count and rate?

## Future protocol versions

8. Should v0.2 support partial reversals or retain settlement-only corrections?
9. Should directional risk attribution for multipart EXCHANGE be standardized rather than excluded from concentration/reciprocity?
10. Should time-duration windows be standardized in addition to deterministic CommunitySequence windows?
11. Which signature/hash algorithms and migration rules should future crypto-agility add?
12. How should contractual metadata commitments support selective disclosure?

## Explicitly deferred

Federation, cross-community IDs/sequences, clearing, portable identity/risk/reputation, global governance, Proof batching, wallets/payments/tokens and The Market remain outside Core v0.1. No global blacklist exists.

## Required before production—not before Core coding

Legal/DPIA review; security review of key/recovery operations; community policy approval; abuse simulations; performance/DoS limits; and operational audit/recovery design.
