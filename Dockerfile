# 프로젝트 루트/Dockerfile
FROM eclipse-temurin:21-jdk-jammy

ARG JAR_FILE=./build/libs/*.jar
COPY ${JAR_FILE} app.jar

ENTRYPOINT ["java", "-jar", "/app.jar"]
