# osTRIS Core v0.1 reference implementation compliance evidence

Evidence date: 2026-08-26

- Normative suite SHA-256: `91bbeaf0bf97b7a20bdbb14f0414a995b5c4a844133b86fd422e6d05a0c828d6`.
- PostgreSQL 17.9 through Testcontainers; Flyway empty database through V2: PASS.
- Final Maven suite: 198 tests, 0 failures, 0 errors, 0 skipped. `validate-idax-module --full`: PASS.
- Normative vectors, transactional authorization, Controller quorum, immutable journal, reconstruction, zero-sum, credit concurrency, restart and governed economics: PASS.
- Identity Continuity PostgreSQL E2E: CONFIRMED social reset preserves the same private RiskSubject; CONTESTED yields review without economic effects; tenant/community/unknown/revoked-assurance cases fail closed; precedence uses CommunitySequence; public DTOs omit private correlation; restart reconstructs the link.
- HTTP security E2E: real random-port Spring Boot/Tomcat, PostgreSQL and RSA JWT validation cover missing, malformed, expired, invalid-signature and valid tokens; immutable tenant claims; permission denial; controlled malformed-input responses; private-link non-disclosure and cross-tenant rejection.
- Database RLS and append-only regressions: PASS. No direct-commit, balance/journal mutation, forced finality, credential rebinding, public-key alias or manual proof-outbox HTTP endpoint exists.
- Apache-2.0 LICENSE is present in every independent repository. Dependency review is recorded in backend `THIRD-PARTY-NOTICES.md`.
- Frontend generated artifacts/12 locales and `validate-idax-module --full`: PASS.

Remaining debt: production legal/DPIA and external security review, operational retention enforcement, and the real IDAX Ledger adapter/anchor receipt reconciliation.

Phase 6 commits: backend `d9db9ec`, `3c1269d`, `f4cd1f2`; frontend `1a85575`; main `b1c052d`; normative-documentation repository `4eb4234`.

## HTTP capability matrix

| Existing endpoint capability | Permission |
| --- | --- |
| Public participant read | `OSTRIS_READ` |
| Transaction proposal | `OSTRIS_TRANSACTION_CREATE` |
| Authorization evidence submission | `OSTRIS_TRANSACTION_AUTHORIZE` |
| Transaction commit | `OSTRIS_TRANSACTION_COMMIT` |
| Private continuity read | `OSTRIS_IDENTITY_CONTINUITY_READ_PRIVATE` |
| Continuity decision creation | `OSTRIS_IDENTITY_CONTINUITY_MANAGE` |

There are no current HTTP endpoints for direct journal mutation, balance adjustment, policy/controller/credential or governance-authority management, Finding/Resolution transitions, or manual proof-outbox insertion. Those absent capabilities are not claimed as implemented.

`LEDGER INTEGRATION: PENDING`
