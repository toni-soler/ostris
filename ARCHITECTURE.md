# Architecture

The implementation is a single-community modular monolith. Pure Java Core types implement wire encoding, accounting, reversal, credit, control-policy and risk rules. Spring services add transactional persistence and HTTP transport without changing those rules.

PostgreSQL owns the durable journal. A pessimistically locked `community.next_sequence` allocates the total order. Accounts are locked in UUID order; floor checks, journal append and projection updates share one database transaction. Committed rows have database triggers rejecting update/delete. `balance_projection` is only a cache and can be reconstructed from journal entries.

Tenant isolation uses the IDAX tenant context plus PostgreSQL RLS. Protocol proof publication uses a transactional outbox behind `ProtocolEventProofService`; a future IDAX Ledger adapter can consume it without coupling Core finality to Ledger availability.
