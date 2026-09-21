# syntax=docker/dockerfile:1
# kukie-server 컨테이너 (DURO-107 4단계). 리버스 프록시 뒤에서 /member/* 가 /member 를 떼고 여기 8080 으로 온다.
# 띄우는 법은 kukie-electron/deploy/README.md — 도커 컴포즈가 이 파일을 빌드하고 DB·Redis·비밀값을 환경변수로 준다
# (SPRING_DATASOURCE_URL · SPRING_DATA_REDIS_HOST · JWT_SECRET · OAuth 두 쌍 · AUTH_COOKIE_SECURE …).

# ── 1) 빌드: CI 와 같은 Temurin 25 (우분투 noble), Gradle 은 래퍼(9.6.1)가 내려받는다 ─────────────────────
# OS 접미사(noble)를 붙여 베이스 OS 는 고정하고 JDK 패치는 따라오게 한다 — useradd/nologin 이 우분투 계열을 전제한다 (PR #27 리뷰)
FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /src
# 빌드 스크립트를 먼저 복사해 의존성 내려받기를 레이어 캐시에 남긴다 — 소스만 바뀌면 여기부터 재사용
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
# 이미지에 필요한 두 구성만 미리 받는다 — 테스트 전용(Testcontainers·mockk·kotest)은 안 받는다. gradlew 는 git 에 실행 권한이 있다
# --configuration 은 단일 값이라 두 번 부른다. 캐시 워밍이라 실패해도 다음 단계(bootJar)가 알아서 받으니 || true
RUN ./gradlew --no-daemon --quiet dependencies --configuration compileClasspath > /dev/null 2>&1 \
 && ./gradlew --no-daemon --quiet dependencies --configuration runtimeClasspath > /dev/null 2>&1 || true
COPY src ./src
# 테스트는 CI 가 돌린다 (Testcontainers 라 도커 안에서는 못 돈다). 산출물 이름은 build.gradle.kts 가 app.jar 로 고정한다 — 글롭으로 고르지 않는다
RUN ./gradlew --no-daemon bootJar -x test

# ── 2) 실행: JRE 만, 루트 아님 ──────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre-noble
WORKDIR /app
# 기본 이미지에 uid 1000(ubuntu)이 이미 있어 번호를 고정하지 않는다 — 이 컨테이너는 볼륨이 없어 uid 가 밖과 맞을 필요가 없다
RUN useradd --system --no-create-home --shell /usr/sbin/nologin kukie
COPY --from=build /src/build/libs/app.jar /app/app.jar
USER kukie
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
