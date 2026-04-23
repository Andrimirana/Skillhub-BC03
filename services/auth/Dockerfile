# Dockerfile Linux - Hyper-V backend (sans WSL2)
# Build : docker build -t cdwfs-auth-app .
# Run   : docker run -p 8080:8080 -e APP_MASTER_KEY="..." cdwfs-auth-app
# Etape 1 : Build Maven
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
COPY mvnw mvnw.cmd pom.xml ./
COPY .mvn .mvn
RUN sed -i 's/\r//' mvnw && chmod +x ./mvnw
RUN ./mvnw dependency:go-offline -B
COPY src ./src
RUN ./mvnw package -DskipTests -B
# Etape 2 : Image finale legere (JRE uniquement)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/auth-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "-jar", "app.jar"]
