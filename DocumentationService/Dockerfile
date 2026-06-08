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

COPY DocumentationService/pom.xml DocumentationService/pom.xml
RUN mvn -B -q -Dmaven.test.skip=true -f DocumentationService/pom.xml dependency:go-offline
COPY DocumentationService/src DocumentationService/src
RUN mvn -B -q -Dmaven.test.skip=true -f DocumentationService/pom.xml package

# ----- runtime stage -----
FROM eclipse-temurin:21-jre-alpine

# Tesseract native binaries + English / Hindi tessdata. Only required when
# DOCUMENT_OCR_PROVIDER=TESSERACT; the STUB engine has zero native deps.
# tess4j also pulls libgomp at runtime.
RUN apk add --no-cache \
        tesseract-ocr \
        tesseract-ocr-data-eng \
        tesseract-ocr-data-hin \
        libgomp
ENV TESSDATA_PREFIX=/usr/share/tessdata

RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/DocumentationService/target/*.jar app.jar

# Uploaded blobs live under this dir when storage-backend=LOCAL. Mount a
# named volume so files survive container restarts; switch to S3 in prod.
ENV DOCUMENT_LOCAL_DIR=/data/documents
RUN mkdir -p /data/documents && chown -R app:app /data
VOLUME ["/data/documents"]

USER app
EXPOSE 8091
HEALTHCHECK --interval=30s --timeout=5s --start-period=150s CMD wget -qO- http://localhost:8091/actuator/health/liveness || exit 1
ENTRYPOINT ["java","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=75.0","-jar","/app/app.jar"]
