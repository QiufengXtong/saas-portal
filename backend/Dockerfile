FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /workspace

COPY . .

RUN mvn -B -ntp clean package -DskipTests

FROM eclipse-temurin:21-jre

RUN groupadd --system spring \
    && useradd --system --gid spring --create-home spring

WORKDIR /app
COPY --from=builder --chown=spring:spring /workspace/platform-boot/target/platform-boot-1.0.0-SNAPSHOT.jar /app/app.jar

USER spring:spring
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
