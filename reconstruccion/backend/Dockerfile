# Etapa 1: compila y corre las pruebas con Maven (imagen basada en Ubuntu, necesaria para protoc)
FROM maven:3.9-eclipse-temurin-21 AS construccion
WORKDIR /app
COPY pom.xml .
COPY src ./src
# El caché de Maven se conserva entre construcciones
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package

# Etapa 2: imagen de ejecución
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=construccion /app/target/reconstruccion-1.0.0.jar app.jar
EXPOSE 8086 9096
ENTRYPOINT ["java", "-jar", "app.jar"]
