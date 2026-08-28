# Glossary — Core v0.1

**protocolDigest** — Lowercase SHA-256 of RFC 8785 JCS UTF-8 bytes of the exact canonical transaction intent. It does not prove commit state, ordering or anchoring.

**ProtocolEventProofPayload v1** — Closed eight-field object binding a committed transaction's protocolDigest to community, unit, transaction, immutable CommunitySequence and `COMMITTED` state.

**proofDigest** — Lowercase SHA-256 of `UTF8("OSTRIS:PROTOCOL:EVENT:PROOF:V1") || 0x00 || JCS_UTF8(ProtocolEventProofPayload)`.

**externalReference** — Stable off-ledger `ostris:v1:<digest>` reference derived with `OSTRIS:LEDGER:EXTERNAL-REF:V1` from communityId and transactionId; it is not part of proofDigest.

**ANCHORED** — Integration state reached only after IDAX Ledger verification returns `VALIDATED_MATCH`; it is independent of economic `COMMITTED` validity.

| Term                       | Definition                                                                                                            |
| -------------------------- | --------------------------------------------------------------------------------------------------------------------- |
| Account                    | Accounting container in one community/unit, controlled through AccountControlPolicy; not user/identity.               |
| AccountControlPolicy       | Versioned controllers, threshold, delegations, validity, revocation and recovery for an account.                      |
| AuthorizationEvidence      | Evidence that an applicable controller/credential requirement was valid at commit sequence.                           |
| AuthorizationPayload       | JCS object containing signed economic intent; signed with domain `OSTRIS:TX:AUTH:V1`.                                 |
| CommunityResolutionAuthority | Versioned threshold authority defining who may approve protocol-permitted governed community resolutions; not an economic account. |
| Controller | Stable opaque protocol authorization principal and the sole unit counted toward M-of-N quorum; not a Participant, RiskSubject or natural-person assertion. |
| ControllerCredentialBinding | Immutable Community-scoped association from one credential ID to one controller ID, with activation and optional revocation sequence. |
| GovernedAuthorization | Transaction-specific authorization signed with `OSTRIS:TX:GOVAUTH:V1`, covering explicitly listed accounts under a permitted final ResolutionBasis. |
| GovernanceAuthorizationPayload | Closed JCS signed intent for governed resolution, binding transaction, entries, basis, authority/version and covered accounts. |
| ResolutionBasis | Closed vocabulary: `FINAL_FINDING`, `FINAL_DISPUTE_RESOLUTION`, `FINAL_DEFAULT_DECISION`, `COMMUNITY_GOVERNANCE_DECISION`. |
| disputeResolutionId | Typed UUIDv7 reference used by imposed RESTITUTION and equal to its persisted FINAL_DISPUTE_RESOLUTION basis ID. |
| Balance                    | Exact reconstructible sum of an account's committed entries.                                                          |
| BasisPoint                 | Integer ratio unit where 10000 is 100%; calculation uses protocol-defined floor division.                             |
| Community                  | Autonomous membership, accounts, unit, policy, journal and sequence scope.                                            |
| CommunityLossAccount       | Governed non-participant account carrying recognized socialized loss.                                                 |
| CommunityPenaltyAccount    | Governed non-participant account receiving economic penalties, separate from loss.                                    |
| CommunitySequence          | Unique monotonic logical order of normative events within one community.                                              |
| Credit floor               | Minimum balance for voluntary exposure, produced by CreditPolicy and separate from balance/liability.                 |
| CreditPolicy               | Policy that calculates voluntary negative capacity; not operation-specific risk.                                      |
| SanctionPolicyConfig       | Closed `OSTRIS-SANCTION-POLICY-1` Boolean permission object for governed PENALTY and imposed RESTITUTION.              |
| DefaultPolicyConfig        | Closed `OSTRIS-DEFAULT-POLICY-1` Boolean permission object for governed full WRITE_OFF.                                |
| Default                    | Outcome of a defined non-recovery process; not negative balance and not necessarily fraud.                            |
| EnforcedLiability          | Projection `max(0, creditFloor-balance)` when balance is below floor due to authorized enforced effects/floor change. |
| Entry                      | Canonical signed integer minor-unit amount applied to an account in a zero-sum transaction.                           |
| Exposure                   | Reconstructible view of origins/counterparties/concentration/timing; not balance.                                     |
| FinalFinding               | Finding whose appeal deadline elapsed or whose appeal was upheld; may authorize culpability-based economic effects.   |
| Finding                    | Reviewed case conclusion with evidence, authority, reason, appeal and sequenced status.                               |
| Identity Assurance         | Limited provider claim with issue/expiry/revocation semantics; not honesty or solvency.                               |
| IdentityContinuityDecision | Sequenced CONFIRMED/CONTESTED/REJECTED decision linking profiles to a private RiskSubject.                            |
| Participant                | Community-scoped social/economic profile for a person or organization.                                                |
| PolicyDecisionSnapshot     | Immutable policy/metric/rule/outcome facts retained with a commit.                                                    |
| PolicySet                  | Coherent immutable policy versions activated at CommunitySequence.                                                    |
| Reputation                 | Multidimensional contestable social/history evidence; not risk, balance or trust.                                     |
| Reversal                   | Unique full transaction negating exactly one non-reversal original while preserving both.                             |
| RiskCase                   | Signals, evidence, affected subjects/transactions, review, Finding, appeal and resolution process.                    |
| RiskMetric                 | One of the five deterministic v0.1 metrics computed from exact sequence-window journal input.                         |
| RiskPolicy                 | Preventive policy evaluating proposals using explainable metrics/rules; establishes no guilt.                         |
| RiskSubject                | Restricted community correlation anchor for CONFIRMED continuity; not public/global person ID.                        |
| Settlement                 | Autonomous non-reversal economic adjustment tied to agreement/case/transaction reference.                             |
| Social reset               | Retirement/new public profile without journal mutation or automatic risk reset.                                       |
| TransactionPurpose         | Closed v0.1 vocabulary: EXCHANGE, REVERSAL, SETTLEMENT, WRITE_OFF, RESTITUTION, PENALTY, LOSS_OFFSET.                 |
| Unit                       | Community accounting unit with immutable UUIDv7/code/scale; not coin, fiat amount or peg.                             |
| Write-off                  | Governed transfer of recognized non-recoverable exposure to CommunityLossAccount.                                     |
| ZOMBIE                     | Negative account/participant inactive under DefaultPolicy, not yet DEFAULTED and capable of returning.                |

Avoid `wallet` absent real custody. Public IDs never use PII or naïve PII hashes. UUID ordering never replaces CommunitySequence.
