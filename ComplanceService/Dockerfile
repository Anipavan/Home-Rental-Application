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

COPY ComplanceService/pom.xml ComplanceService/pom.xml
RUN mvn -B -q -Dmaven.test.skip=true -f ComplanceService/pom.xml dependency:go-offline
COPY ComplanceService/src ComplanceService/src
RUN mvn -B -q -Dmaven.test.skip=true -f ComplanceService/pom.xml package

# ----- runtime stage -----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/ComplanceService/target/*.jar app.jar

# GST invoice PDFs are written under this dir; mount a volume in compose to
# preserve them across container restarts.
ENV COMPLIANCE_INVOICE_DIR=/data/gst-invoices
RUN mkdir -p /data/gst-invoices && chown -R app:app /data
VOLUME ["/data/gst-invoices"]

USER app
EXPOSE 8093
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s CMD wget -qO- http://localhost:8093/actuator/health/liveness || exit 1
ENTRYPOINT ["java","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=75.0","-jar","/app/app.jar"]
