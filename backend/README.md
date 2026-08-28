# idax-ostris-backend

Spring Boot/PostgreSQL reference backend for osTRIS Core v0.1. Economic state is derived exclusively from the immutable journal. Identity Continuity is private, community-scoped and CommunitySequence-ordered; public Participant responses omit RiskSubject identifiers.

Security consumes the IDAX JWT/TenantContext contract and explicit osTRIS permissions. Tenant context comes from the validated JWT claim, not request headers or payloads. No direct-commit, balance-adjustment, journal-mutation or manual proof-outbox endpoint exists.

## Protocol event proof delivery

Every successful economic commit writes the immutable journal transaction and one
`protocol_proof_outbox` row in the same PostgreSQL transaction. The outbox stores the
transaction-intent `protocol_digest` separately from the cached ProtocolEventProof v1
`proof_digest`, opaque `external_reference`, and exact external canonicalization profile.
The journal remains the authority: the worker reconstructs and compares every digest and
reference before making an external call.

The scheduled worker uses a short `FOR UPDATE SKIP LOCKED` claim transaction, releases the
database connection, then calls only the IDAX Ledger Proof HTTP API. It never contains XRPL
keys, provider logic, economic entries, or journal JSON. It acquires a tenant-scoped SERVICE
token through the shared IDAX `ServiceTokenProvider`, with audience `idax-ledger` and only
`LEDGER_PROOF_CREATE`, `LEDGER_READ`, and `LEDGER_PROOF_VERIFY`. `ANCHORED` means the verify
response is exactly `VALIDATED_MATCH`; proof creation alone never changes economic state or
establishes anchoring.

Delivery is disabled by default (`OSTRIS_LEDGER_ENABLED=false`). An explicit disabled-mode
delivery invocation moves pending traces to `DISABLED` without HTTP; economic commits still
succeed. Basic transport failures become `FAILED_RETRYABLE`; cached-integrity failures,
idempotency conflicts, and verification mismatches become `FAILED_PERMANENT`.

### Reliable delivery state machine

Delivery is intentionally **at least once**, not distributed exactly once. Deterministic
`external_reference` is both the Ledger `externalId` and `Idempotency-Key`; every retry sends
the same immutable body. Ledger therefore collapses ambiguous or repeated POSTs to one logical
Proof. PostgreSQL coordinates workers without an in-memory distributed lock.

`PENDING` and due `FAILED_RETRYABLE` rows can be claimed. `PROCESSING` rows can be reclaimed
after `processing-lease` (default two minutes). Each claim/reclaim receives a fresh opaque
`claim_token` and increments `attempt_count` exactly once. Every result transition uses
`id + PROCESSING + claim_token` CAS; late results are discarded. `ANCHORED` and
`FAILED_PERMANENT` are never automatically claimed. Historical `DISABLED` rows also remain
disabled when configuration is later enabled; an explicit operational policy is required to
reactivate them, and no override endpoint is provided.

Retry delay is deterministic and unbounded in attempt count:
`min(maximumRetryDelay, initialRetryDelay * 2^(attempt_count-1))`, defaulting to 10 seconds and
15 minutes. Polling does not increment attempts. Valid `Retry-After` on HTTP 429 acts as a
minimum delay. There is no maximum-attempt abandonment for infrastructure failures.

When `proof_reference` exists, the worker GETs and verifies that Proof rather than POSTing a
new one. Without a reference—also after an ambiguous POST or crash before linkage—it repeats
the identical idempotent create. Ledger states map as follows:

| Ledger/API outcome | osTRIS action | Operator attention | ANCHORED |
|---|---|---:|---:|
| `PENDING`, `SIGNED`, `SUBMITTED`, `FAILED_RETRYABLE` | bounded retry/reconcile | no | no |
| `VALIDATED` + verify `VALIDATED_MATCH` | terminal success | no | yes |
| verify `NOT_FOUND`, `NOT_VALIDATED`, `PROVIDER_UNAVAILABLE` | bounded retry/reconcile | no | no |
| `FAILED_PERMANENT`, `ATTEMPT_EXPIRED` | permanent | yes | no |
| verify `ANCHOR_MISMATCH`, integrity mismatch | permanent | yes | no |
| HTTP 400/401/403/404/409 | permanent configuration/integrity review | yes | no |
| HTTP 429/5xx, DNS/refused/connect/read timeout | bounded retry | no | no |
| malformed response | permanent contract review | yes | no |

Ledger source defines `ATTEMPT_EXPIRED` as a signed XRPL attempt past its last-ledger sequence;
automatic re-signing is forbidden and Ledger excludes it from automatic reconciliation.
osTRIS therefore stops automatic delivery and never constructs/resubmits XRPL material.
`VALIDATED_FAILURE`, `FOUND_NOT_VALIDATED`, and `NOT_FOUND` remain Ledger-provider inspection
states: Ledger maps them respectively to permanent failure, later reconciliation, or internal
expiry/retry handling; osTRIS observes only the application Proof/verify contract.

Crash windows converge safely: a crash before linkage repeats idempotent create; after linkage
it GETs the exact Proof; after `VALIDATED_MATCH` but before the DB update it reverifies; after
`ANCHORED` no delivery work is reclaimed. Claims and result updates are short independent DB
transactions and no connection or row lock is held during token or Ledger HTTP work.

Micrometer exposes outbox counts per state, oldest pending/processing ages, attempts,
successes, classified failures, stale results, idempotency conflicts, and verification
mismatches. The health contributor always keeps economic processing `AVAILABLE`; delivery is
reported as `DEGRADED` when retryable/permanent rows exist. Batches are bounded, the scheduler
does not overlap within an instance, and one configured JDK HTTP client/pool is reused.

Persisted `last_error` contains only a bounded classification code—never a stack trace,
Authorization header, token, secret, canonical journal, amounts, or remote payload.

Provision the production identity through the IDAX Platform superuser API: create client
`ostris-ledger-delivery`, rotate its credential once and retain the returned secret in a
secret manager, then add one grant per authorized tenant with audience `idax-ledger` and the
three permissions above. Supply the secret at runtime through
`OSTRIS_LEDGER_SERVICE_CLIENT_SECRET`; never store it in this repository. Platform token
issuance must be enabled and Ledger must trust the same SERVICE issuer.

License: Apache-2.0. See `THIRD-PARTY-NOTICES.md`. IDAX Ledger integration remains pending.
