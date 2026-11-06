# 공통 API → 고객 카운터 중계 (DURO-196)

기존 회원·개인 키로 고객 카운터를 호출하는 첫 버전이다. **고객이 공개한 HTTPS 카운터에 직접 연결**한다. 고객 사설망에 자동으로 연결하거나 Kubernetes API를 추론 프록시로 쓰지 않는다. 역방향 터널·애드온 자동 설치는 후속 범위다. 이 변경은 GPU를 기동하거나 기존 독립 GPU 포털·키를 변경하지 않는다.

## 연결 등록과 운영 허용 목록

1. 운영자는 `SERVING_RELAY_ALLOWED_HOSTS=api.customer-a.example,api.customer-b.example`처럼 정확한 호스트 목록을 배포 환경에 설정한다. 기본값은 비어 있어 등록·연결을 거절한다. wildcard·임의 사용자 URL을 허용하지 않는다.
2. 고객 카운터에 별도의 중계 서비스 키를 발급한다. Kukie 개인 키·로그인 JWT·내부 토큰을 재사용하지 않는다. 카운터는 이 서비스 키로 요청을 허용하도록 고객 관리자가 구성해야 한다.
3. 해당 클러스터의 기존 팀 ADMIN이 아래 관리 API로 주소·서비스 키·허용 모델을 등록한다. 키는 기존 `KUKIE_CREDENTIAL_KEY`로 AES-GCM 암호화해 V26 테이블에 저장한다. 이 암호화 키의 보존·회전 정책은 기존 클러스터 자격증명과 같다.
4. 실제 도메인·인증서·방화벽·카운터 모델 설정은 고객/운영자가 준비해야 한다. 등록은 주소 정책을 검사할 뿐 연결 성공이나 GPU 준비를 보증하지 않는다. GET models 및 시험 호출로 확인한다.

| 메서드·경로 | 동작 |
| --- | --- |
| PUT `/clusters/{clusterId}/serving/endpoint` | 기존 Kukie 로그인으로 해당 ADMIN만 구성·교체. `{"baseUrl":"https://api.customer.example/v1","serviceToken":"<서비스 키>","models":["model-a"]}`. 204 |
| GET `/clusters/{clusterId}/serving/endpoint` | 해당 ADMIN에게 주소·모델만 반환. 미설정 시 200 빈 본문(클라이언트는 null로 정규화), 서비스 키/암호문은 반환하지 않음 |
| DELETE `/clusters/{clusterId}/serving/endpoint` | 해당 ADMIN만 연결 제거. 204. 이후 새 요청은 503 |

클러스터당 제공 대상은 하나이며 모델 이름을 그대로 전달한다. 서비스 키는 ASCII 가시문자 1~4096자(공백 없음), 모델은 1~100개, 모델명은 영숫자로 시작하고 영숫자·`.`·`_`·`/`·`-`로 최대 200자다. PUT은 전체 교체이며 모델·서비스 키를 모두 다시 보낸다. 연결 제거·교체 전에 이미 준비된 요청은 기존 연결로 완료할 수 있다.

HTTPS 443만 허용하고 base 경로는 `/v1`로 정규화한다. userinfo·query·fragment·다른 경로/포트는 거절한다. DNS 결과 중 하나라도 사설·loopback·link-local·metadata·CGNAT·multicast·예약/우회 주소이면 거절한다. 등록 시뿐 아니라 **실제 소켓 연결을 위한 DNS resolver에서 검증한 IP를 반환**하므로 검증 후 다른 DNS 해석으로 내부망에 접속하지 않는다. 연결 풀은 이미 공개 IP에 열린 소켓만 재사용한다. 원래 호스트의 TLS 인증서·이름 검증은 유지한다. 환경 프록시·redirect·자동 retry·쿠키 저장을 사용하지 않는다.

## 사용자 공통 API

백엔드 기준 base URL은 `/serving/v1`이다. 공개 도메인 및 프록시 라우팅은 배포에서 이 경로를 보존하도록 구성해야 한다. 아래 구현만으로 기존 `/v1` 주소가 자동 교체되는 것은 아니다.

| 메서드·경로 | 동작 |
| --- | --- |
| GET `/serving/v1/models` | 키의 고객 카운터에서 목록을 조회하고 등록 모델과 교집합만 반환. id/object/owned_by만 공개 |
| POST `/serving/v1/chat/completions` | OpenAI 호환 JSON을 해당 카운터에 전달. `model` 필수, `stream`은 생략/false 또는 true |

`Authorization: Bearer <Kukie 개인 키>`만 인정한다. 쿠키·로그인 JWT는 대체 인증이 아니다. 키에서 도출한 클러스터로만 라우팅하고 URL·헤더로 대상을 선택하지 않는다. 모든 신규 요청은 키·만료·폐기 및 API 이용 권한 ACTIVE를 확인한다. 사용자 요청 헤더는 전달하지 않고 카운터용 Authorization과 필요한 HTTP 콘텐츠 헤더만 새로 만든다. 사용자 키는 카운터로 가지 않는다.

요청 JSON은 최대 1 MiB. JSON object·문자열 model·boolean stream을 검사하고 중복 필드를 거절한다. model은 등록 목록과 일치해야 한다. 나머지 messages/tools/생성 옵션 등은 카운터가 검증하며 본문 바이트는 그대로 전달한다. 지원하지 않는 query는 거절한다. embeddings/completions/files/responses 등 다른 경로는 지원하지 않는다.

일반 응답은 최대 8 MiB, SSE는 총 64 MiB다. SSE 바이트·줄바꿈·댓글·`[DONE]`은 변경 없이 chunk 단위로 flush한다. 자체 delta/error/heartbeat를 끼워 넣지 않는다. 따라서 카운터가 자체 heartbeat를 보내야 장시간 무출력 연결을 유지할 수 있다. SSE 시작 후 실패는 연결 종료로 알리며 HTTP 200을 뒤늦게 다른 상태로 바꿀 수 없다. 클라이언트는 `[DONE]` 없는 종료를 완료로 간주하면 안 된다.

오류는 `{"error":{"message":"…","type":"kukie_error","code":"…"}}` 형식이다. 상위 오류 본문·URL·서비스 인증정보는 반환하지 않는다.

| 상태 | 의미 |
| --- | --- |
| 400 / 413 | 잘못된 요청 / 본문 크기 초과 |
| 401 | 개인 키 누락·불명·만료·폐기·권한 취소(같은 오류) |
| 403 | 등록되지 않은 모델 |
| 429 | 이 서버 또는 키 동시 요청 상한 / 고객 카운터의 429. code로 구분 |
| 503 | 연결 미설정 / 고객 카운터 404·503(모델 이용 불가). code로 구분 |
| 502 | 상위 인증 실패·redirect·연결/프로토콜 실패. code로 구분 |
| 504 | 응답 시작 전 유휴·전체 시간 제한 |

## 연결·취소·시간 상한

기본값은 서버 인스턴스별 동시 32개, 키별 4개, 연결 5초, 유휴 읽기 30초, 전체 중계 300초다. 설정은 `SERVING_RELAY_MAX_CONNECTIONS`, `SERVING_RELAY_MAX_CONNECTIONS_PER_KEY`, `SERVING_RELAY_CONNECT_SECONDS`, `SERVING_RELAY_IDLE_SECONDS`, `SERVING_RELAY_TOTAL_SECONDS`로 바꾼다. 전체 상한은 응답 헤더·본문 전송을 포함한다. 요청 본문 수신에는 배포 HTTP 서버의 업로드 시간 제한도 적용해야 한다. 여러 서버를 합친 전역 한도나 토큰 예산은 아직 구현하지 않았다.

요청마다 가상 스레드 하나를 쓰며 별도 deadline이 상위 요청을 cancel한다. Servlet async timeout은 추가 5초의 최종 정리 장치다. 클라이언트 연결 오류·write 실패도 상위를 cancel한다. 연결이 조용할 때 HTTP 서버가 클라이언트 단절을 즉시 감지하지 못할 수 있으므로 유휴/전체 상한이 정리를 보장한다. 실제 상위 작업이 종료될 때 슬롯을 반환한다. 카운터도 클라이언트 단절을 vLLM 취소로 연결해야 GPU 생성이 중단되며, 이 마지막 구간은 실제 카운터 종단 시험 대상이다.

권한/키 폐기 커밋 이후 시작된 인증은 거절한다. 이미 인증한 요청은 완료를 허용한다. DB 트랜잭션을 DNS·HTTP·스트리밍 중 유지하지 않는다.

## 사용량 계약과 데이터 처리

Kukie 서버를 프롬프트·응답이 통과한다. 고객 밖으로 데이터가 나가지 않는 구조가 아니다. 본문·개인 키·서비스 키·해시는 DB/감사 로그에 기록하지 않는다. 프록시/APM/Apache HTTP wire의 헤더·본문 로깅도 켜면 안 된다. 응답 캐시는 `no-store`, 버퍼링은 끈다. 외부 프록시에도 스트리밍/시간 제한 설정을 맞춰야 한다.

`ServingUsageObservation` 이벤트에 key/access/cluster/user ID, 모델, 시작 시각·소요 시간, 처리 결과·상태, 입출력 토큰 수와 usageMissing만 발행한다. 프롬프트·답변·원문 자격증명은 없다. 일반 JSON의 usage와 SSE의 한 줄 data JSON usage를 읽는다. SSE 이벤트 최대 64 KiB까지만 관찰하고 더 큰 줄은 버린다(전달은 유지). 토큰이 없거나 `[DONE]` 없이 끝나거나 실패하면 usageMissing이다. 스트리밍 사용량이 필요하면 호출자가 `stream_options.include_usage=true`를 설정해야 한다. 토큰이 없다고 0으로 추정하지 않는다.

DURO-197에서 시작 기록과 종료 이벤트를 PostgreSQL에 연결했다. [사용량 런북](serving-usage.md)의 90일 보관·중복/누락·재시작 미완료 정책을 따른다. 고객 카운터에는 서비스 키만 보이므로 사용자별 집계는 중앙의 키 식별 기록을 사용한다. 정확한 과금·토큰 예산은 아직 제공하지 않는다.

## 검증·배포·롤백

가짜 회사 A/B 카운터의 키·라우팅 격리, 원형 SSE, 본문 없는 usage, 취소·timeout·슬롯 반환을 통합 시험했다. 실제 HTTP 클라이언트의 스트림 종료와 504, 실제 로컬 HTTPS 소켓의 인증서 이름·DNS 주입·redirect 차단·읽기 timeout·cancel도 시험한다. `src/test/resources/serving/counter-test.p12`는 공개된 test-only 비밀번호의 테스트 인증서이며 운영 신뢰 저장소에는 추가하지 않는다.

전체 서버 build와 보안 annotation convention 검사를 수행한다. 운영 반영에는 V24→V25→V26, 호스트 허용 목록, 서비스 키 등록, 공통 공개 경로·TLS·프록시 설정 및 실제 카운터 시험이 필요하다. GPU를 켜야 하는 실제 추론 검증은 별도 운영 단계다. 롤백은 이전 앱/공개 라우팅으로 되돌리고 추가 테이블을 보존한다. 서비스 키 유출 시 고객 카운터에서 폐기·재발급한 뒤 PUT으로 교체한다.
