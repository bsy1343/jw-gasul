# Dockerfile — 멀티스테이지 빌드 (Gradle / Spring Boot 4.1 / JDK 21)
# CI:     docker build --target ci .   (빌드 + 테스트, .github/workflows/ci.yml)
# Deploy: docker build -t jw-gasul .   (런타임 이미지, .github/workflows/deploy.yml)
#
# 중요: 임베디드 PostgreSQL(test 프로필)은 initdb를 root로 실행할 수 없다.
#       따라서 테스트 실행과 런타임 모두 비root 사용자로 동작시킨다.

# ── Stage 1: 빌드 (실행 가능 bootJar 생성, 테스트는 제외) ──────────────
FROM eclipse-temurin:21-jdk AS build
# 비root 빌드/테스트 사용자 (임베디드 PG가 root 실행을 거부하므로)
RUN useradd -m -u 1001 builder
WORKDIR /app
RUN chown builder:builder /app
USER builder
# Gradle 캐시(배포판+의존성)를 사용자 홈에 두어 ci 스테이지에서 그대로 재사용
ENV GRADLE_USER_HOME=/home/builder/.gradle
# 래퍼·빌드 스크립트를 먼저 복사해 다운로드를 레이어 캐시로 재사용
COPY --chown=builder:builder gradlew settings.gradle.kts build.gradle.kts ./
COPY --chown=builder:builder gradle ./gradle
# 의존성·Gradle 배포판을 먼저 받아 레이어 캐시로 고정.
# 빌드 스크립트가 안 바뀌면 이 레이어가 재사용되어, 코드만 바뀔 땐 의존성 재다운로드를 건너뛴다.
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies -q || true
COPY --chown=builder:builder src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

# ── Stage 2: CI 검증 (테스트) ────────────────────────────────────────
# build 스테이지를 그대로 이어받아 비root(builder)로 테스트 → 임베디드 PG(linux-arm64v8) 정상 기동
FROM build AS ci
RUN ./gradlew --no-daemon test

# ── Stage 3: 런타임 (배포 기본 스테이지) ─────────────────────────────
FROM eclipse-temurin:21-jre AS runtime
# 비root 실행 사용자. test 프로필 배포 시 임베디드 PG도 root 아님으로 정상 기동.
RUN useradd -m -u 1001 appuser
WORKDIR /app
# 기본은 prod(실 PostgreSQL). 배포 compose에서 SPRING_PROFILES_ACTIVE로 덮어쓸 수 있다.
ENV SPRING_PROFILES_ACTIVE=prod
ENV UPLOAD_DIR=/data/uploads
RUN mkdir -p /data/uploads && chown -R appuser:appuser /app /data
COPY --from=build --chown=appuser:appuser /app/build/libs/*.jar app.jar
USER appuser
# 실제 포트는 SERVER_PORT 환경변수로 결정(application.yml). 아래는 문서용 기본값.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
