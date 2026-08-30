# osTRIS deployment and operations quickstart

This document is the public-safe operational source for the standalone osTRIS
reference implementation. Commands target Linux with Docker Engine and Docker
Compose v2.

## Prerequisites

- Git, `curl`, Docker Engine and `docker compose`.
- At least 3 GB of free RAM for PostgreSQL and the backend.
- Port `8095` must be free.

## Start

```shell
git clone https://github.com/toni-soler/ostris.git
cd ostris/deployment
cp .env.example .env
```

Edit `.env` and replace `OSTRIS_DB_PASSWORD=change-me`. Do not commit the file.
Then start PostgreSQL, the public IDAX Core migrations and osTRIS:

```shell
docker compose up -d --build
docker compose ps
curl --fail http://localhost:8095/actuator/health/readiness
```

The backend is healthy when the last command returns a response whose status is
`UP`. The frontend is an IDAX host extension and is not a standalone SPA.

## Authentication and ledger integration

The default local profile uses `deployment/keys/dev-public.pem`, a development
public key with no private counterpart in the repository. Mount the public key
of the real JWT issuer for authenticated use. Never place a private key in the
repository.

Ledger delivery is disabled by default with `OSTRIS_LEDGER_ENABLED=false`.
Enabling it requires a reachable, correctly configured IDAX Ledger service and
matching authentication. First validate osTRIS without ledger delivery, then
enable the integration and verify both services' logs and health endpoints.

## Routine operations

```shell
# Follow application logs
docker compose logs -f --tail=200 backend

# Inspect every service
docker compose ps -a

# Restart only osTRIS
docker compose restart backend

# Stop containers while preserving PostgreSQL data
docker compose down

# Start again using the existing database volume
docker compose up -d
```

Back up the PostgreSQL volume before upgrades. Protocol events and proof-delivery
state are persistent business data; do not treat the database as disposable
outside development.

## Update

```shell
cd ostris
git pull --ff-only
cd deployment
docker compose build --pull
docker compose up -d
docker compose ps
curl --fail http://localhost:8095/actuator/health/readiness
```

The `core-migrations` one-shot service applies pending public IDAX Core Flyway
migrations before osTRIS starts. Do not modify an already-applied migration.

## Stop and remove development data

`docker compose down` preserves data. The following command permanently deletes
the Compose volumes and is only appropriate for an intentional development
reset:

```shell
docker compose down --volumes
```

## Troubleshooting

```shell
docker compose config
docker compose ps -a
docker compose logs --tail=300 postgres core-migrations backend
```

- If PostgreSQL is unhealthy, verify `.env`, the password and volume
  permissions.
- If `core-migrations` fails, inspect its logs before restarting the backend.
- An HTTP `401` or `403` usually indicates a JWT public-key, issuer or claims
  mismatch.
- When ledger delivery is enabled, distinguish osTRIS persistence failures from
  downstream IDAX Ledger connectivity or authentication failures.

This repository is a reference implementation and does not claim production
readiness or an external security audit. Production deployment requires TLS,
external secret management, monitored backups, resource limits, hardened
networking and operational monitoring.
