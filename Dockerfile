FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17.0.10_7-jre-alpine
WORKDIR /app
RUN apk add --no-cache bash curl

COPY --from=build /app/target/*.jar app.jar

ADD https://raw.githubusercontent.com/vishnubob/wait-for-it/master/wait-for-it.sh /wait-for-it.sh
RUN chmod +x /wait-for-it.sh

EXPOSE 8080

# Sendam waits for its DB AND the Nexah API
ENTRYPOINT ["/wait-for-it.sh", "sendam-postgres:5432", "--", "/wait-for-it.sh", "nexah-simulator-container:8383", "--", "java", "-Dspring.profiles.active=docker", "-jar", "app.jar"]