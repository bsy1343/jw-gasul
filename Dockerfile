# jw-gasul 멀티스테이지 빌드 (Gradle)
# CI:     docker build --target ci .
# Deploy: docker build -t jw-gasul . && docker run -d jw-gasul

# Stage 1: 빌드 (실행 가능 jar 생성, 테스트는 ci 스테이지에서)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --version --no-daemon
COPY src src
RUN ./gradlew bootJar --no-daemon

# Stage 2: CI 검증 (테스트)
FROM build AS ci
RUN useradd -m ci && chown -R ci:ci /app
USER ci
RUN ./gradlew test --no-daemon

# Stage 3: 런타임 (JRE)
# ★ 반드시 비root로 실행한다 — 배포가 test 프로필(임베디드 PostgreSQL)로 뜨는데
#   임베디드 PG의 initdb는 root 실행을 거부해서, root로 두면 기동 즉시 죽는다.
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
ENV TZ=Asia/Seoul
RUN useradd -m -u 1001 appuser \
    && mkdir -p /app/data/uploads \
    && chown -R appuser:appuser /app
COPY --from=build --chown=appuser:appuser /app/build/libs/*.jar app.jar
USER appuser
EXPOSE 18084
ENTRYPOINT ["java", "-jar", "app.jar"]