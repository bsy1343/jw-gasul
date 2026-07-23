# Dockerfile — 멀티스테이지 빌드 (Gradle / Spring Boot 4.1 / JDK 21)
# CI:     docker build --target ci .   (빌드 + 테스트, .github/workflows/ci.yml)
# Deploy: docker build -t jw-gasul .   (런타임 이미지, .github/workflows/deploy.yml)

# ── Stage 1: 빌드 (실행 가능 bootJar 생성, 테스트는 제외) ──────────────
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
# Gradle 래퍼·빌드 스크립트를 먼저 복사해 배포판/의존성 다운로드를 레이어 캐시로 재사용
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon --version
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

# ── Stage 2: CI 검증 (테스트) ────────────────────────────────────────
# 임베디드 PostgreSQL은 컨테이너(리눅스 arm64) 내부에서 실행된다(linux-arm64v8 바이너리 필요).
FROM build AS ci
RUN ./gradlew --no-daemon test

# ── Stage 3: 런타임 (배포 기본 스테이지) ─────────────────────────────
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
# 운영은 prod 프로필(실 PostgreSQL). DB 접속 정보·업로드 경로는 배포 compose의 환경변수로 주입.
ENV SPRING_PROFILES_ACTIVE=prod
ENV UPLOAD_DIR=/data/uploads
RUN mkdir -p /data/uploads
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
