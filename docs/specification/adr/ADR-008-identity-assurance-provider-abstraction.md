# ADR-008 — Identity Assurance provider abstraction

Status: Accepted — 2026-08-24, Phase 3 continuity semantics

## Context

KYC is one assurance mechanism and source evidence should not be copied into osTRIS by default.

## Decision candidate

Provider-neutral IdentityVerificationProvider issues minimal, expiring/revocable IdentityAssuranceClaims. Legal identity, Participant and public profile remain separate. Public protocol IDs never use PII or naïve hashes.

## Consequences

Opaque pairwise/community subject references are preferred. Otherwise sequenced CONFIRMED/CONTESTED/REJECTED decisions and appeal apply. Exact provider evidence remains policy/legal context.
