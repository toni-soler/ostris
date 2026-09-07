# osTRIS 0.2.0 release notes

osTRIS 0.2.0 adopts the shared permission-catalog lifecycle from IDAX Core
0.2.0 and corrects service-token exchange with the host platform by sending
JSON requests with explicit content negotiation.

The protocol, canonicalization and normative test-vector formats are unchanged.
The increment concentrates on runtime integration, permission ownership and
standalone security composition.

Upgrade order:

1. Back up the osTRIS PostgreSQL database.
2. Update to IDAX Core runtime 0.2.0 and apply its migrations.
3. Deploy osTRIS 0.2.0.
4. Verify readiness, permission registration and service-token exchange.
5. If Ledger delivery is enabled, verify proof delivery and acknowledgements.
