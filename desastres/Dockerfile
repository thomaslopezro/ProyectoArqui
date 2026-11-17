FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package

FROM payara/server-full:6.2025.11-jdk17
USER root
RUN mkdir -p /opt/payara/registro && chown payara:payara /opt/payara/registro
USER payara
COPY --from=build /app/target/gestion-emergencias.war /opt/payara/deployments/gestion-emergencias.war
EXPOSE 8080
