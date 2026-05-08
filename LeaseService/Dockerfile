# Self-contained build — see auth-service/Dockerfile for the rationale.
# Run from repo root via `build.context: .` in docker-compose.yml.

# ----- build stage -----
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY KafkaEvents/pom.xml KafkaEvents/pom.xml
COPY KafkaEvents/src     KafkaEvents/src
RUN mvn -B -q -DskipTests -f KafkaEvents/pom.xml install

COPY auth-commons/pom.xml auth-commons/pom.xml
COPY auth-commons/src     auth-commons/src
RUN mvn -B -q -DskipTests -f auth-commons/pom.xml install

COPY LeaseService/pom.xml LeaseService/pom.xml
RUN mvn -B -q -DskipTests -f LeaseService/pom.xml dependency:go-offline
COPY LeaseService/src LeaseService/src
RUN mvn -B -q -DskipTests -f LeaseService/pom.xml package

# ----- runtime stage -----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/LeaseService/target/*.jar app.jar

# Lease deeds (PDFs) are stored under this dir.
ENV LEASE_DEED_DIR=/data/lease-deeds
RUN mkdir -p /data/lease-deeds && chown -R app:app /data
VOLUME ["/data/lease-deeds"]

USER app
EXPOSE 8090
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s CMD wget -qO- http://localhost:8090/actuator/health/liveness || exit 1
ENTRYPOINT ["java","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=75.0","-jar","/app/app.jar"]
