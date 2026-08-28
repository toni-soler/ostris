# Protocol compliance

Status: **CORE COMPLIANCE PASS** for the osTRIS Core v0.1 reference implementation.

Implemented and tested: exact Core v0.1 normative vectors; RFC 8785/JCS strict input; UUIDv7; double-entry zero sum; immutable journal and CommunitySequence; credit-floor concurrency; Controller authorization and governed economics; private sequenced Identity Continuity with restart; JWT, permission and tenant-bound HTTP boundaries; PostgreSQL RLS; reconstruction; and atomic proof outbox persistence.

The controlled vector copy is `backend/src/test/resources/normative/core-v0.1.json`. Expected suite digest: `91bbeaf0bf97b7a20bdbb14f0414a995b5c4a844133b86fd422e6d05a0c828d6`.

See `REFERENCE_IMPLEMENTATION_COMPLIANCE.md` for bounded evidence. This is not a production-readiness, formal-verification or external-security-audit claim.

**LEDGER INTEGRATION: PENDING.** Core correctness and transaction finality do not depend on that adapter.
