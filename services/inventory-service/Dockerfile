FROM maven:3.9.15-eclipse-temurin-26 AS build
WORKDIR /workspace
COPY pom.xml .
COPY libs ./libs
COPY services ./services
RUN mvn -q -pl services/inventory-service -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
ARG GIT_SHA=unknown
LABEL org.opencontainers.image.source="https://github.com/JasonTM17/AgriCore_SpringBoot_Microservices" \
      org.opencontainers.image.licenses="MIT" \
      org.opencontainers.image.revision="${GIT_SHA}"
RUN addgroup -S agricore && adduser -S agricore -G agricore \
 && apk add --no-cache wget
WORKDIR /app
COPY --from=build /workspace/services/inventory-service/target/inventory-service-*.jar app.jar
USER agricore
EXPOSE 8086
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8086/actuator/health || exit 1
ENTRYPOINT ["java","-XX:+UseContainerSupport","-jar","/app/app.jar"]
