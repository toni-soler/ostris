# osTRIS Core Reference Implementation

This repository contains the executable osTRIS Core reference implementation
and the normative protocol specification. It is a sanitized public snapshot
with history independent from the private development repositories.

## Repository layout

- `backend/`: Java 21 and Spring Boot protocol implementation.
- `frontend/`: IDAX-compatible frontend extension.
- `deployment/`: deployment composition and operational entry point.
- `docs/specification/`: normative osTRIS specification, schemas and vectors.
- root architecture and compliance documents: bounded implementation evidence.

The current compliance statement does not claim production readiness, formal
verification or an external security audit.

## IDAX runtime dependency

The backend requires the closed-source Maven artifact
`es.idynamicsax.idax:idax-core`. It does not require `idax-legacy` and contains
no Dynamics AX integration. Core source, source JARs and IDAX generators are not
published here. See the companion
[`idax-core-runtime`](https://github.com/toni-soler/idax-core-runtime)
repository for database prerequisites and binary license terms. Maven downloads
the binary anonymously; no GitHub token or manual installation is required.

For an unpublished local Core build:

```shell
cd backend
mvn test -Didax.version=0.0.1-SNAPSHOT
```

## Build

```shell
cd backend
mvn test

cd ../frontend
npm ci
npm test
npm run build
```

## License

The source in this repository is licensed under Apache License 2.0. The
separately distributed `idax-core` binary has its own license and is not covered
by this repository's Apache license.
