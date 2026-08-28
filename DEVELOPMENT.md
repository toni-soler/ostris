# Development

Install `idax-core` locally, then run `mvn test` in `backend`. Run the backend generator with `python scripts/gen-idax-ostris/gen-idax-ostris.py`; validate drift using `python scripts/validate-generated.py`. Run `npm test` in `frontend`.

The database needs IDAX Core migrations/roles first. osTRIS migrations use schema `ostris` and read tenant context from `app.tenant_id`.
