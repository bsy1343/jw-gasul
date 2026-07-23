# DevForge 멀티스테이지 빌드
# CI:     docker build --target ci .
# Deploy: docker build -t devforge . && docker run -d devforge

# Stage 1: 빌드
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B
COPY src src
RUN ./mvnw package -DskipTests -B

# Stage 2: CI 검증 (빌드 + 테스트)
FROM build AS ci
RUN ./mvnw test -B

# Stage 3: 런타임
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
