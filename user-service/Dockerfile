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

COPY user-service/pom.xml user-service/pom.xml
RUN mvn -B -q -Dmaven.test.skip=true -f user-service/pom.xml dependency:go-offline
COPY user-service/src user-service/src
RUN mvn -B -q -Dmaven.test.skip=true -f user-service/pom.xml package

# ----- runtime stage -----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/user-service/target/*.jar app.jar

ENV USER_UPLOAD_DIR=/data/uploads
RUN mkdir -p /data/uploads && chown -R app:app /data
VOLUME ["/data/uploads"]

USER app
EXPOSE 8089
HEALTHCHECK --interval=30s --timeout=5s --start-period=150s CMD wget -qO- http://localhost:8089/actuator/health/liveness || exit 1
ENTRYPOINT ["java","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=75.0","-jar","/app/app.jar"]
