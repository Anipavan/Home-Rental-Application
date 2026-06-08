# Self-contained build: this Dockerfile is run from the repository root
# (build.context: . in docker-compose.yml). It bakes the shared SNAPSHOT
# libraries (KafkaEvents, auth-commons) into the build stage's local m2,
# so cold builds work without pre-installing anything on the host.

# ----- build stage -----
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /workspace

# 1. Build & install KafkaEvents (rarely changes — kept early for cache reuse).
COPY KafkaEvents/pom.xml KafkaEvents/pom.xml
COPY KafkaEvents/src     KafkaEvents/src
RUN mvn -B -q -Dmaven.test.skip=true -f KafkaEvents/pom.xml install

# 2. Build & install auth-commons.
COPY auth-commons/pom.xml auth-commons/pom.xml
COPY auth-commons/src     auth-commons/src
RUN mvn -B -q -Dmaven.test.skip=true -f auth-commons/pom.xml install

# 3. Resolve service deps offline, then compile + package.
COPY auth-service/pom.xml auth-service/pom.xml
RUN mvn -B -q -Dmaven.test.skip=true -f auth-service/pom.xml dependency:go-offline
COPY auth-service/src auth-service/src
RUN mvn -B -q -Dmaven.test.skip=true -f auth-service/pom.xml package

# ----- runtime stage -----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/auth-service/target/*.jar app.jar

USER app
EXPOSE 9090
HEALTHCHECK --interval=30s --timeout=5s --start-period=150s CMD wget -qO- http://localhost:9090/actuator/health/liveness || exit 1
ENTRYPOINT ["java","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=75.0","-jar","/app/app.jar"]
