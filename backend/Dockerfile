# Contexto de build: raíz del monorepo (ver docker-compose.yml -> backend.build.context: .)

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY backend/pom.xml .
RUN mvn -q -B dependency:go-offline || true
COPY backend/src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

ARG GTFS_VALIDATOR_VERSION=8.0.1
RUN mkdir -p /app/validator && \
    (curl -fsSL -o /app/validator/gtfs-validator-cli.jar \
      "https://github.com/MobilityData/gtfs-validator/releases/download/v${GTFS_VALIDATOR_VERSION}/gtfs-validator-${GTFS_VALIDATOR_VERSION}-cli.jar" \
      || echo "WARN: no se pudo descargar gtfs-validator; la validacion oficial quedara deshabilitada")

COPY database/migrations /app/database/migrations
COPY --from=build /build/target/gtfs-platform-backend.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
