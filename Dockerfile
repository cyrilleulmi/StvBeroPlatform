# One container for the whole platform (E-83): Quarkus serves the API and the Angular app on port 8080.
# Without STV_DB_URL a throwaway PostgreSQL starts inside the container (docker/start.sh).
#
#   docker build --build-arg PROFILE=preview -t stvbero .   # test environments: seed, /dev-login, simulators
#   docker build --build-arg PROFILE=prod -t stvbero .      # production (later, after the login, O-01)
#
# PROFILE is a build property in Quarkus (stv.test-support) and a build configuration in Angular (environment),
# so it is fixed when the image is built.

FROM node:22-bookworm-slim AS frontend
ARG PROFILE=prod
WORKDIR /src/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN if [ "$PROFILE" = "preview" ]; then CONFIGURATION=preview; else CONFIGURATION=production; fi \
 && npx ng build --configuration "$CONFIGURATION"

FROM maven:3.9-eclipse-temurin-21 AS backend
ARG PROFILE=prod
WORKDIR /src/backend
COPY backend/pom.xml ./
RUN mvn -B -q dependency:go-offline || true
COPY backend/src ./src
COPY --from=frontend /src/frontend/dist/frontend/browser/ ./src/main/resources/META-INF/resources/
RUN mvn -B -q package -DskipTests -Dquarkus.profile="$PROFILE"

FROM eclipse-temurin:21-jre
ARG PROFILE=prod
# PostgreSQL for the test environments (docker/start.sh): a throwaway database inside the container
RUN export DEBIAN_FRONTEND=noninteractive \
 && apt-get update \
 && apt-get install -y --no-install-recommends postgresql \
 && rm -rf /var/lib/apt/lists/*
ENV QUARKUS_PROFILE=$PROFILE \
    JAVA_OPTS="-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError"
WORKDIR /app
COPY --from=backend /src/backend/target/quarkus-app/ ./
COPY docker/start.sh /app/start.sh
RUN chmod 755 /app/start.sh
USER 1000
EXPOSE 8080
CMD ["/app/start.sh"]
