# Backend

Quarkus 3.40, Java 21, PostgreSQL, Flyway.

```bash
../scripts/db-start.sh        # Datenbank (ohne Docker)
mvn quarkus:dev               # Profil dev: http://localhost:8080, Seed wird beim Start geladen
mvn verify                    # alle Tests (Profil test, Datenbank localhost:5432/stv)
```

- Migrationen: `src/main/resources/db/migration` (bestehende nie ändern, immer neue Version)
- Seed: `ch.stvbero.platform.kern.testhilfen.Seed` (docs/testdaten.md)
- Swagger UI: http://localhost:8080/q/swagger-ui · OpenAPI: http://localhost:8080/q/openapi
