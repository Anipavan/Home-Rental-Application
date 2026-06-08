# ----- build stage -----
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q -e -Dmaven.test.skip=true dependency:go-offline
COPY src ./src
RUN mvn -B -q -Dmaven.test.skip=true package

# ----- runtime stage -----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar

USER app
EXPOSE 8761
HEALTHCHECK --interval=30s --timeout=5s --start-period=150s CMD wget -qO- http://localhost:8761/actuator/health/liveness || exit 1
ENTRYPOINT ["java","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=75.0","-jar","/app/app.jar"]
