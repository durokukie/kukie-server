# 공통 API 종단 검증 (DURO-198)

## 이번 범위

2026-11-06 사용자는 **현재는 로컬 종단 검증까지만** 진행하도록 정했다. 실제 GPU 기동·추론, 운영 카운터/키 구성, 도메인 변경은 실행하지 않았다. 아래 자동 시험의 통과를 실제 고객 GPU/공개 스트리밍 검증 완료로 해석하지 않는다. 기존 시험 포털·기존 키의 종료/이관도 범위 밖이다.

## 자동 실행

```sh
./gradlew test --tests 'com.duro.kukie.serving.e2e.*' --no-daemon --max-workers=2
./gradlew build --no-daemon --max-workers=2
```

JDK 25와 Docker(Testcontainers PostgreSQL 18/Redis 8)가 필요하다. macOS Colima는 기존 테스트와 같이 `DOCKER_HOST` 및 `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`를 설정한다. Spring은 임의 로컬 HTTP 포트에서 실제 인증 인터셉터·컨트롤러·서비스·Flyway/JPA/JDBC를 사용한다. 원문 키/토큰을 명령행·실행 로그에 출력하지 않는다.

고객 카운터 두 개는 실제 로컬 HTTPS 소켓이다. 서버의 일반 HTTPS 요청 구현으로 통신하며 테스트 CA/테스트 DNS와 로컬 포트 매핑만 테스트 전송 계층에서 주입한다. 운영의 HTTPS 443·허용 호스트·SSRF·인증서 이름 검증을 완화하는 설정은 추가하지 않는다. 개별 DNS/IPv6/redirect/TLS 검사와 함께 실행한다.

## 시나리오와 실행 근거

`src/test/kotlin/com/duro/kukie/serving/e2e/ServingEndToEndTest.kt` 및 `LocalHttpsCounters.kt`:

| 시나리오 | 확인 |
| --- | --- |
| 기존 계정 → 실제 로그인 → A/B 이메일 초대 수락 → 키 발급 | 같은 계정의 기존 팀은 그대로이고 A/B 팀·클러스터 관리 권한이 생기지 않음 |
| 관리자/이용자 API | A 관리자가 B 관리 정보에 접근 불가, 이용자에게 허용 대상만 반환, 내부 주소·자격증명 없음 |
| A/B 키와 같은 모델 이름 | 키에서 정한 HTTPS 카운터만 호출, 서비스 인증으로 교체, 원문 개인 키/로그인 쿠키를 전달하지 않음 |
| models/일반 JSON/SSE | 모델 목록 공개 범위, 회사별 응답, CRLF/댓글/이벤트/usage/[DONE] 원형 전달 |
| 사용량 | 실제 중계 종료가 PostgreSQL에 저장되고 고객·사용자·키별 요청·입출력 토큰을 정확히 분리, 본인 응답의 타인 집계 없음 |
| 폐기·취소·재초대·만료 | 이전 권한/키는 살아나지 않고 다른 회사 키는 유지, 미인증/잘못된 키도 401 |
| 상위 오류 | 429·503·redirect·연결 종료·유휴 timeout을 구분하고 상위 비밀 오류 본문은 숨김, 자동 retry 없음 |
| 스트림 중도 종료 | HTTP 200이라도 [DONE]/usage가 없으면 INCOMPLETE/집계 누락으로 저장 |
| 키 동시 상한·클라이언트 취소 | 실제 스트림 진행 중 두 번째 요청은 429, 취소 후 CANCELLED 원장과 슬롯 반환, 다음 호출 성공 |
| 개인정보/비밀 | 원장 JSON 및 시험 중 표준 서버 로그에 요청/응답 canary·원문 개인/서비스 키가 없음 |

반복 취소/완료 콜백이 종료 DB 트랜잭션을 인터럽트하는 경합은 요청 작업과 완료 기록 작업을 분리해 막는다. 취소 후 Servlet 응답이 먼저 재활용되는 경우에도 기록이 사라지지 않도록 전송 상태를 별도로 보관하고 늦은 응답 객체 조회를 안전하게 처리한다. `ServingRelayIntegrationTest`는 완료 기록을 의도적으로 대기시킨 뒤 콜백을 반복해도 CANCELLED 기록이 남는지 검사한다.

기존 전체 회귀 검사도 함께 실행해 회원·팀·권한·관리자 모델 대화·키·보안 annotation·동시성 기능을 확인한다. 상세 결과 XML은 `build/test-results/test/`에 있으며 생성물은 Git에 넣지 않는다.

4단계 프론트 리뷰의 연결 미설정 문제는 별도로 수정했다. 서버가 미설정 endpoint를 200 빈 본문으로 주는 계약을 이 종단 시험에서 확인하고, 프론트는 undefined를 null로 정규화하되 권한 404를 숨기지 않는다. 프론트의 타입/린트/1,126개 Vitest/production build 및 재리뷰 후 머지했다.

## 공개 환경에서 읽기만 확인한 사실

2026-11-06 KST 관측 시점:

- `https://dev.durokukie.com/member/actuator/health`: 200, UP.
- `https://dev.durokukie.com/member/serving/v1/models`: 인증 없이 401 `SERVING_KEY_INVALID`.
- 기존 `https://api.gpu.durokukie.com/v1/models`: 인증 없이 401 `invalid_api_key`.
- GPU 클러스터의 ServedModel과 GPU 노드 조회 결과 0개. 변경하거나 기동하지 않음.
- 개발 클러스터 member/web Deployment는 각 Ready 1. 이 관측만으로 모든 최신 기능의 실제 배포/마이그레이션 완료를 단정하지 않음.
- 기존 Caddy는 `/member` 접두사를 떼고 Spring으로 전달. CloudFront는 API 캐시 비활성, viewer 요청 전달, 전체 HTTP 메서드 허용이며 읽기 유휴 60초. 이는 소스 확인이며 실제 인증된 SSE의 전체 경로 시험을 대체하지 않음.

## 아직 확인하지 않은 운영 구간

1. 4단계 머지 커밋/이미지·V27·프론트 번들이 실제 환경에 반영됐는지 최종 대조.
2. 운영 허용 호스트·공개 주소 설정과 기존 관리자 계정으로 카운터용 별도 서비스 키 등록. 비밀은 Git·Terraform state·PR·채팅에 넣지 않음.
3. 실제 공개 주소에서 개인 키로 models/일반 추론/SSE/권한 취소·사용량을 확인.
4. 고객 카운터 연결 취소가 vLLM 생성 중단까지 전달되는지, 프록시 버퍼링·TLS·유휴/전체 제한을 확인.
5. 실제 GPU 시험은 별도 기동 승인 후 진행. 기존 모델/키를 임의 중지하거나 교체하지 않음. 이번에 제안한 g6e.xlarge 1대·gpt-oss-20b·모델 자동 종료 30분 시험은 실행하지 않음.

실제 모델이 없거나 연결을 미설정한 상태를 성공으로 꾸미지 않는다. 코드 머지·CI·로컬 통합 통과와 서비스 운영 성공을 각각 기록한다.
