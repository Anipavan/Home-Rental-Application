# Self-contained build — see auth-service/Dockerfile for the rationale.
# Run from repo root via `build.context: .` in docker-compose.yml.

# ----- build stage -----
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY KafkaEvents/pom.xml KafkaEvents/pom.xml
COPY KafkaEvents/src     KafkaEvents/src
RUN mvn -B -q -Dmaven.test.skip=true -f KafkaEvents/pom.xml install

COPY auth-commons/pom.xml auth-commons/pom.xml
COPY auth-commons/src     auth-commons/src
RUN mvn -B -q -Dmaven.test.skip=true -f auth-commons/pom.xml install

COPY property-service/pom.xml property-service/pom.xml
RUN mvn -B -q -Dmaven.test.skip=true -f property-service/pom.xml dependency:go-offline
COPY property-service/src property-service/src
RUN mvn -B -q -Dmaven.test.skip=true -f property-service/pom.xml package

# ----- runtime stage -----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/property-service/target/*.jar app.jar

# Persistent uploads dir (override with PROPERTY_UPLOAD_DIR env)
ENV PROPERTY_UPLOAD_DIR=/data/uploads
RUN mkdir -p /data/uploads && chown -R app:app /data
VOLUME ["/data/uploads"]

USER app
EXPOSE 8088
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s CMD wget -qO- http://localhost:8088/actuator/health/liveness || exit 1
ENTRYPOINT ["java","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=75.0","-jar","/app/app.jar"]
