FROM maven:3.9.9-eclipse-temurin-17 AS dependencies
WORKDIR /build
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp dependency:go-offline

FROM dependencies AS build
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp verify

FROM eclipse-temurin:17-jre-noble
WORKDIR /app
RUN groupadd --system diary && useradd --system --gid diary diary && mkdir -p /data/images && chown -R diary:diary /data /app
COPY --from=build /build/target/mint-diary-1.0.0.jar /app/app.jar
USER diary
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
