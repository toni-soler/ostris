# osTRIS deployment

Linux development composition with PostgreSQL, the public IDAX Core migrations
and the osTRIS backend. The frontend is an IDAX host extension, not an
independent SPA.

```shell
cp .env.example .env
# Change OSTRIS_DB_PASSWORD in .env
docker compose up -d --build
docker compose ps
curl --fail http://localhost:8095/actuator/health/readiness
```

`keys/dev-public.pem` es una clave pública de arranque sin clave privada
distribuida. En un entorno real debe montarse la clave pública del emisor JWT.
