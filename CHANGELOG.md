# Changelog

## [0.4.0] - 2026-09-23

- Require IDAX Core Runtime 0.4.0.
- Preserve the normative protocol, proof delivery and frontend extension
  behavior of 0.3.
- Confirm that generated metadata is bundled at build time and does not require
  a separate runtime metadata-fetch cache.

## [0.3.0] - 2026-09-09

- Require IDAX Core Runtime 0.3.0.
- Add the integrated public frontend extension to the IDAX Shell local stack.
- Support authenticated module routing and local Maven candidate validation.
- Preserve the normative protocol specification and verification vectors.

## [0.2.0] - 2026-09-01

- Register osTRIS permissions through the shared IDAX module-catalog lifecycle.
- Compose the shared permission service explicitly for standalone execution.
- Send service-token requests as JSON and verify the platform exchange through
  HTTP and integration tests.
- Expand PostgreSQL security, permission registration and application
  composition coverage.
- Require the public IDAX Core runtime 0.2.0.

## [0.1.0] - 2026-08-28

- Initial public reference implementation and normative specification snapshot.
- Durable protocol-event proof delivery to IDAX Ledger.
- Linux Docker Compose evaluation deployment.
