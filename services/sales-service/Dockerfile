# syntax=docker/dockerfile:1.7
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY libs ./libs
COPY services ./services
RUN --mount=type=cache,id=agricore-maven,target=/root/.m2,sharing=locked \
    mvn -q -pl services/sales-service -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S agricore && adduser -S agricore -G agricore \
 && apk add --no-cache wget
WORKDIR /app
COPY --from=build /workspace/services/sales-service/target/sales-service-*.jar app.jar
USER agricore
EXPOSE 8091
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8091/actuator/health || exit 1
ENTRYPOINT ["java","-XX:+UseContainerSupport","-jar","/app/app.jar"]
