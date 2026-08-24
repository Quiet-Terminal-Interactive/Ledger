FROM maven:3.9-eclipse-temurin-25-alpine AS build
WORKDIR /build

COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

RUN addgroup -S ledger && adduser -S ledger -G ledger
COPY --from=build /build/target/ledger-*.jar app.jar
USER ledger

EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=5 \
    CMD wget -qO- http://localhost:8080/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
