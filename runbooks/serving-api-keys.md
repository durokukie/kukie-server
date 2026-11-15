# 개인 API 키 (DURO-195)

기존 Kukie 로그인 계정과 수락한 API 이용 권한(`ServingAccess`)을 재사용한다. Flyway V25로 키 테이블을 추가한다. 관리 API와 인증 서비스를 제공한다. 공통 추론 중계는 [중계 런북](serving-relay.md)에 정리했고 화면·사용량 저장·집계는 후속 단계다. 기존 독립 GPU 포털 키는 이 테이블로 자동 이전하거나 호환하지 않는다.

## 사용·관리 API

다음 경로는 모두 기존 Kukie 로그인 JWT/쿠키 인증을 사용한다. 발급한 API 키로 로그인하거나 키를 발급할 수 없다.

| 메서드·경로 | 동작·권한 |
| --- | --- |
| POST `/serving/accesses/{accessId}/keys` | ACTIVE 권한 소유자만 발급. `{"name":"개발","expiresInDays":90}`. 201, 원문 `token`과 메타데이터 `key` 반환. `Cache-Control: no-store` |
| GET `/serving/accesses/{accessId}/keys?page=0&size=20` | 본인 키의 메타데이터만 조회. 취소된 권한의 과거 키도 본인이 정리 가능 |
| DELETE `/serving/accesses/{accessId}/keys/{keyId}` | 본인 키 폐기, 204. 재폐기도 204 |
| GET `/clusters/{clusterId}/serving/accesses/{accessId}/keys?page=0&size=20` | 해당 클러스터 팀 ADMIN만 대상 사용자의 메타데이터 조회 |
| DELETE `/clusters/{clusterId}/serving/accesses/{accessId}/keys/{keyId}` | 해당 클러스터 팀 ADMIN이 대상 키 폐기, 204 |

페이지는 0부터, size 1~100. 목록은 최신 생성순으로 `content,page,size,totalElements,totalPages`를 반환한다. 메타데이터는 `id,accessId,name,prefix,createdAt,expiresAt,revokedAt`이며 원문·해시는 포함하지 않는다. 관리자가 사용자 대신 키를 발급하지 않는다. 관리자가 직접 API를 쓰려면 본인 이메일도 초대·수락해야 한다.

이름은 공백만 불가, 최대 80자. `expiresInDays` 생략/null은 90일, 선택 범위 1~365일이다. 유효 기간은 UTC 발급 시각부터 계산한다. 권한별 미폐기·미만료 키 최대 5개다. 상한 도달 시 409. 기존 키를 폐기하거나 만료되면 추가 발급할 수 있다. 교체는 새 키를 발급하고 클라이언트에 적용한 뒤 옛 키를 폐기한다. 5개가 모두 유효하면 먼저 불필요한 키 하나를 폐기한다.

## 저장·인증·취소

키는 CSPRNG 32바이트(256비트), `kk_` + Base64URL 43자로 만든다. DB에는 SHA-256 64자리와 식별용 앞 12자만 저장한다. 고엔트로피 비밀이므로 비밀번호와 달리 빠른 SHA-256을 쓴다. 원문은 발급 응답에서만 반환하며 조회·복원·재표시하지 않는다. 응답 객체의 `toString`도 원문을 가린다. 애플리케이션은 원문·해시를 로그로 쓰지 않으며 외부 프록시/APM에도 Authorization 및 발급 응답 본문 로깅을 켜면 안 된다.

키는 불변 accessId에 귀속되고 사용자·클러스터는 해당 권한에서 도출한다. 클러스터 잠금 → 권한 확인 → 키 개수 확인·발급/폐기를 한 트랜잭션에서 수행해 동시 발급과 권한 취소를 직렬화한다. 다른 대상의 keyId를 경로에 넣어도 조회·폐기할 수 없다. 클러스터·이용자 삭제는 기존 권한 FK 및 키 FK CASCADE로 키까지 제거한다.

후속 중계는 `AuthenticateServingApiKeyService(token)`을 매 신규 요청에서 호출한다. 캐시 없이 해시 조회, 키 미폐기·미만료, 권한 ACTIVE를 확인하고 `(keyId,accessId,clusterId,userId)`만 반환한다. 잘못된 형식·알 수 없는 키·만료·폐기·권한 취소 모두 같은 401 오류다. 사용자 입력 대상 주소를 신뢰하지 않고 반환된 clusterId를 사용해야 한다. 이 서비스 호출에 사용한 트랜잭션은 외부 I/O 전에 종료한다. DURO-196의 중계 진입점이 이 서비스를 사용한다.

권한 취소 커밋 이후 시작되는 인증은 거절한다. 취소와 동시에 진행되는 요청 중 이미 인증 판정을 통과한 요청/스트림은 완료를 허용하며 강제 중단하지 않는다. 키의 revokedAt이 null이어도 권한이 REVOKED이면 사용할 수 없다. 권한 취소는 키의 시간을 덮어쓰지 않으며 재초대는 새 accessId라 옛 키가 살아나지 않는다.

사용자 키는 Kukie 진입 인증용이다. 고객 카운터 인증은 별도 서비스 자격증명으로 분리하고 사용자 키를 그대로 전달하지 않는다. DURO-196에서 공개 HTTPS 카운터와 별도 서비스 키로 구체화했다.

## 검증·배포

`ServingApiKeyIntegrationTest`는 원문 1회/해시 저장, 소유자·관리자 격리, 회사 간 경로 변조, 폐기·만료·재초대, 동시 발급 상한과 발급/권한취소 경합, 삭제 cascade를 검증한다. `ServingApiKeyTest`는 만료 시각 경계를 검증한다. 전체 서버 build와 보안 annotation convention 테스트도 통과해야 한다.

배포 시 V24 이후 V25가 적용되어야 한다. 이 변경만으로 추론 API가 공개되거나 GPU가 켜지지는 않는다. 롤백은 이전 애플리케이션으로 되돌리되 추가 테이블은 보존한다. 이미 발급한 키를 보존해야 하므로 운영 DB의 키 테이블을 삭제하지 않는다.
