# ADR-012 — Governed Authorization for Community Resolutions

Status: **Accepted**

## Problem

Requiring voluntary AccountControlPolicy consent from every affected account makes an imposed penalty, adjudicated remedy or default write-off vetoable by the subject. Conversely, a global administrator bypass would destroy protocol authorization and auditability.

## Alternatives

1. Require all account signatures: rejected because imposed resolutions become optional.
2. Give an administrator universal mutation power: rejected because it is unscoped, unauditable protocol bypass.
3. Define a separate constrained community authorization: accepted.

## Decision

A versioned `CommunityResolutionAuthority` uses explicit stable controllers and M-of-N threshold. A `GovernanceAuthorizationPayload` is signed under `OSTRIS:TX:GOVAUTH:V1` and binds the exact transaction, purpose, entries, permitted final ResolutionBasis, authority/version and explicitly covered accounts. It can substitute AccountControlPolicy only for the closed purpose/basis matrix. EXCHANGE is forbidden. Effective policy, authority, controller binding, credential state, threshold, basis finality and coverage are re-evaluated at commit sequence.

The unit of quorum is distinct `controllerId`. Credentials and keys are mechanisms, not votes. Controller-to-credential bindings are Community-scoped and immutable for the lifetime of a credential ID; overlapping credentials support safe rotation without increasing quorum. Rebinding and the same public key representing different eligible controllers are rejected. AccountControlPolicy uses exactly the same quorum semantics.

## Security consequences

There is no force/admin endpoint or balance mutation. Cross-transaction, cross-community, amount, basis and account replay fail cryptographically. Revoked credentials fail according to CommunitySequence. Evidence persists without private keys. Communities may separate reviewers, finding authorities and resolution controllers.
