# Backend

Quarkus 3.40, Java 21, PostgreSQL, Flyway.

```bash
../scripts/db-start.sh        # database (without Docker)
mvn quarkus:dev               # profile dev: http://localhost:8080, seed is loaded on start
mvn verify                    # all tests (profile test, database localhost:5432/stv)
```

- Migrations: `src/main/resources/db/migration` (never change existing ones, always add a new version; one-time exception: F-10 rewrote them in English, so a local database from before F-10 needs `../scripts/db-start.sh reset`)
- Seed: `ch.stvbero.platform.core.testsupport.Seed` (docs/test-data.md)
- Swagger UI: http://localhost:8080/q/swagger-ui · OpenAPI: http://localhost:8080/q/openapi
