# jw-gasul 멀티스테이지 빌드 (Gradle)
# CI:     docker build --target ci .
# Deploy: docker build -t jw-gasul . && docker run -d jw-gasul

# Stage 1: 빌드 (실행 가능 jar 생성, 테스트는 ci 스테이지에서)
WORKDIR /app
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --version --no-daemon
COPY src src
RUN ./gradlew bootJar --no-daemon

# Stage 2: CI 검증 (테스트)
FROM build AS ci
RUN ./gradlew test --no-daemon

# Stage 3: 런타임 (JRE)
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
ENV TZ=Asia/Seoul
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]