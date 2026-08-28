# ADR-013 — Typed governed-economic policy permissions

Status: Accepted

## Decision

Core v0.1 keeps Policy (what), CommunityResolutionAuthority (who), ResolutionBasis (why), GovernanceAuthorization (exact cryptographic approval) and journal semantics (how) separate. No second authoritative economic-decision entity is introduced.

SanctionPolicy and DefaultPolicy governed-economic permissions are closed typed JSON objects: `OSTRIS-SANCTION-POLICY-1` and `OSTRIS-DEFAULT-POLICY-1`. All fields are required and exactly typed; unknown fields reject. Evaluation uses the prospective commit CommunitySequence and only narrows the fixed purpose/basis matrix.

LOSS_OFFSET and adjudicated REVERSAL/SETTLEMENT need no additional configurable policy. Special-account purposes are protocol-fixed. WRITE_OFF v0.1 is full-only.

## Consequences

Implementations can derive PERMITTED/REJECTED deterministically without scripts, AI or subjective code. They may retain the effective policy-version reference for audit, but a cached evaluation never becomes an authorization source.
