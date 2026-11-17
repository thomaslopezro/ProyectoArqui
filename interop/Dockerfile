FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY settings.xml .
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -s settings.xml -Daether.connector.http.connectTimeout=15000 -Daether.connector.http.requestTimeout=30000 -U -B compile dependency:copy-dependencies -DoutputDirectory=target/lib
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/classes ./classes
COPY --from=build /app/target/lib ./lib
ENTRYPOINT ["java","-cp","classes:lib/*","PruebaRpc"]
