# syntax=docker/dockerfile:1.7
FROM maven:3.9.9-eclipse-temurin-21@sha256:3a4ab3276a087bf276f79cae96b1af04f53731bec53fb2e651aca79e4b10211e AS build
WORKDIR /workspace
COPY pom.xml .
COPY libs ./libs
COPY services ./services
RUN --mount=type=cache,id=agricore-maven,target=/root/.m2,sharing=locked \
    mvn -q -pl services/work-service -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine@sha256:3f08b13888f595cc49edabea7250ba69499ba25602b267da591720769400e08c
RUN addgroup -S agricore && adduser -S agricore -G agricore \
 && apk add --no-cache wget
WORKDIR /app
COPY --from=build /workspace/services/work-service/target/work-service-*.jar app.jar
USER agricore
EXPOSE 8085
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8085/actuator/health || exit 1
ENTRYPOINT ["java","-XX:+UseContainerSupport","-jar","/app/app.jar"]
