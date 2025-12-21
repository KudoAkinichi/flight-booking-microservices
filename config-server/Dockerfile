# ========================
# BUILD STAGE
# ========================
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# ========================
# RUNTIME STAGE
# ========================
FROM openjdk:17-jdk-slim
WORKDIR /app

ENV SPRING_PROFILES_ACTIVE=prod

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8888
ENTRYPOINT ["java","-jar","app.jar"]
