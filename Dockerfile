# syntax=docker/dockerfile:1
# kukie-server 컨테이너 (DURO-107 4단계). 리버스 프록시 뒤에서 /member/* 가 /member 를 떼고 여기 8080 으로 온다.
# 띄우는 법은 kukie-electron/deploy/README.md — 도커 컴포즈가 이 파일을 빌드하고 DB·Redis·비밀값을 환경변수로 준다
# (SPRING_DATASOURCE_URL · SPRING_DATA_REDIS_HOST · JWT_SECRET · OAuth 두 쌍 · AUTH_COOKIE_SECURE …).

# ── 1) 빌드: CI 와 같은 Temurin 25, Gradle 은 래퍼(9.6.1)가 내려받는다 ─────────────────────
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
# 빌드 스크립트를 먼저 복사해 의존성 내려받기를 레이어 캐시에 남긴다 — 소스만 바뀌면 여기부터 재사용
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies --quiet > /dev/null 2>&1 || true
COPY src ./src
# 테스트는 CI 가 돌린다 (Testcontainers 라 도커 안에서는 못 돈다). 지금은 bootJar 만 돌아 jar 가 하나지만, 나중에 jar 태스크가
# 같이 돌아 *-plain.jar 이 생겨도 실행 가능한 boot jar 만 집히게 걸러 둔다
RUN ./gradlew --no-daemon bootJar -x test \
 && cp "$(ls build/libs/*.jar | grep -v -- '-plain.jar' | head -1)" /src/app.jar

# ── 2) 실행: JRE 만, 루트 아님 ──────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre
WORKDIR /app
# 기본 이미지에 uid 1000(ubuntu)이 이미 있어 번호를 고정하지 않는다 — 이 컨테이너는 볼륨이 없어 uid 가 밖과 맞을 필요가 없다
RUN useradd --system --no-create-home --shell /usr/sbin/nologin kukie
COPY --from=build /src/app.jar /app/app.jar
USER kukie
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
