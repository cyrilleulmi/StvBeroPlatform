# Backend

Quarkus 3.40, Java 21, PostgreSQL, Flyway.

```bash
../scripts/db-start.sh        # database (without Docker)
mvn quarkus:dev               # profile dev: http://localhost:8080, seed is loaded on start
mvn test                      # all tests (profile test, database localhost:5432/stv)
mvn verify -Pcoverage         # all tests with the coverage gate, report in target/site/jacoco (as in scripts/test.sh)
mvn quarkus:test              # continuous testing: reruns the tests a saved class touches
```

- Modules: one package per module directly below `ch.stvbero.platform` (`core`, `notification`, `attendance`, `swissunihockey`, `driver`, `statistics`, `testsupport`). What may depend on what is in `../modules.json`; the tests in `src/test/java/ch/stvbero/platform/architecture` enforce it together with the conventions and the class size (docs/architecture.md)

- Migrations: `src/main/resources/db/migration` (never change existing ones, always add a new version; one-time exception: F-10 rewrote them in English, so a local database from before F-10 needs `../scripts/db-start.sh reset`)
- Seed: `ch.stvbero.platform.testsupport.Seed` (docs/test-data.md)
- Swagger UI: http://localhost:8080/q/swagger-ui · OpenAPI: http://localhost:8080/q/openapi
