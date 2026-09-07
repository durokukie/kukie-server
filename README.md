# kukie-server

Kukie의 REST API 서버입니다.

## 기술 스택

- Kotlin 2.3 / Java 25
- Spring Boot 4.1 (Web MVC, Data JPA, Data Redis, Validation, Mail, Actuator)
- PostgreSQL 18 + Flyway (스키마 마이그레이션)
- Redis 8 (리프레시 토큰, 이메일 인증 코드 저장)
- JWT 기반 인증, OAuth2 로그인 (GitHub, Google)

## 시작하기

### 사전 요구사항

- JDK 25
- Docker (PostgreSQL / Redis 컨테이너 실행용)

### 환경 변수 설정

`.env.example`을 복사해 `.env`를 만들고 값을 채웁니다.

```bash
cp .env.example .env
```

### 실행

```bash
./gradlew bootRun
```

`spring-boot-docker-compose`가 `docker-compose.yaml`의 PostgreSQL과 Redis 컨테이너를 자동으로 띄우므로, Docker만 실행 중이면 별도 준비 없이 서버가 시작됩니다. 스키마는 시작 시 Flyway가 자동으로 마이그레이션합니다.

### 테스트

```bash
./gradlew test
```

통합 테스트는 Testcontainers(PostgreSQL, Redis)를 사용하므로 Docker가 실행 중이어야 합니다.

## API 문서

서버 실행 후 Swagger UI에서 API 명세를 확인할 수 있습니다.

- http://localhost:8080/swagger-ui.html

## 기여

브랜치 전략, 커밋 컨벤션 등은 [CONTRIBUTING.md](CONTRIBUTING.md)를 참고하세요.
