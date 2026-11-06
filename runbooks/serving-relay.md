# 공통 API → 고객 카운터 중계 (DURO-196)

기존 회원·개인 키로 고객 모델을 호출한다. **명시적으로 등록한 공개 HTTPS 카운터가 있으면 우선 사용**하고, 없으면 **기존에 연결된 FULL EKS의 Kubernetes Service 프록시를 자동 사용**한다. 새 도메인·서비스 키·DB 자격증명을 자동 생성하지 않고 이미 등록된 EKS 연결을 재사용한다. 온프레미스/GENERIC 클러스터의 자동 연결·역방향 터널·애드온 자동 설치는 포함하지 않는다. GPU를 기동하거나 기존 독립 GPU 포털·키를 변경하지 않는다.

## EKS 자동 연결

1. 관리자에게 기존 AWS 연결이 있고, 해당 EKS의 적용된 `accessLevel`이 FULL이며 권한 적용 실패가 없어야 한다. 연결의 팀·AWS 계정도 기존 `EksGpuAccess` 검사로 일치해야 한다. FULL 해제·연결 삭제 시 새 자동 중계를 막는다.
2. 오븐이 관리하는 `llm` 네임스페이스의 ServedModel 목록을 읽는다. 경로 이름과 Hugging Face 모델 식별자가 유효하고 삭제 중이 아닌 모델만 후보로 인정한다. Ready 모델만 GET models와 화면의 제공 모델에 노출한다. CRD/네임스페이스가 없으면 빈 목록이다. 모델이 없다면 chat은 503 `SERVING_MODEL_UNAVAILABLE`, 등록된 후보가 Loading 등이라면 상위 호출 없이 503으로 기록한다.
3. 공개 모델 ID는 ServedModel의 `metadata.name`(예: `gpt-oss-20b`)이다. `model`만 실제 `spec.source.huggingface`(예: `openai/gpt-oss-20b`)로 바꾸고 나머지 요청 필드는 유지한다. SSE·일반 JSON은 OpenAI 형식으로 그대로 전달하며 대화 화면의 delta/done 이벤트로 바꾸지 않는다.
4. 서버가 보관한 API 서버 주소·CA·AWS 연결로 EKS 토큰을 발급하고 `/api/v1/namespaces/llm/services/{name}:http/proxy/v1/chat/completions`를 호출한다. 사용자 키/JWT/쿠키나 지정 URL은 EKS에 전달하지 않는다. 사용자에게 API 서버 주소·CA·계정 자격증명을 공개하지 않는다.
5. EKS API 서버는 Kukie 서버에서 도달 가능해야 한다. 별도 모델 도메인/인증서가 필요 없다는 뜻이며, private-only EKS에 새 네트워크 경로가 생긴다는 뜻은 아니다. 기존 GPU 대화가 가능했던 EKS 접속 조건과 같다.
6. 개인 API 키 인증·권한 취소·동시 슬롯·사용량 시작/종료 기록은 기존 중계와 동일하다. 카운터 프로세스를 통과하지 않으므로 카운터 자체의 GPU별 admission 제어·메트릭은 자동으로 적용되지 않는다. EKS API 서버를 추론 데이터 경로로 사용하므로 대규모 트래픽의 전용 데이터 경로를 대체한다고 보증하지 않는다.
7. 목록 조회는 별도 서버 인스턴스 동시 상한(중계 전체 상한과 같은 수) 내에서만 실행한다. 한 번에 100개/본문 4 MiB까지 읽고 continue가 있는 잘린 목록은 부분 공개하지 않고 502로 거절한다. 목록/권한 조회는 중계 시작 전이며 중계 사용량·전체 스트림 타이머에 포함되지 않는다. 외부 I/O 중 DB 트랜잭션을 유지하지 않는다.

관리자·이용자 info 응답의 `connectionMode`는 `EKS_PROXY` / `HTTPS_COUNTER` / `UNCONFIGURED`다. `configured=true`인 EKS도 Ready 모델이 없으면 models는 빈 목록이며 GPU를 자동 기동하지 않는다. 별도 카운터 주소/서비스 키는 자동 경로에서 입력하지 않는다. 외부 카운터 등록은 선택적 override이며 해제하면 연결 가능한 EKS의 자동 경로로 돌아간다.

## 선택적 외부 카운터 등록과 운영 허용 목록

1. 운영자는 `SERVING_RELAY_ALLOWED_HOSTS=api.customer-a.example,api.customer-b.example`처럼 정확한 호스트 목록을 배포 환경에 설정한다. 기본값은 비어 있어 등록·연결을 거절한다. wildcard·임의 사용자 URL을 허용하지 않는다.
2. 고객 카운터에 별도의 중계 서비스 키를 발급한다. Kukie 개인 키·로그인 JWT·내부 토큰을 재사용하지 않는다. 카운터는 이 서비스 키로 요청을 허용하도록 고객 관리자가 구성해야 한다.
3. 해당 클러스터의 기존 팀 ADMIN이 아래 관리 API로 주소·서비스 키·허용 모델을 등록한다. 키는 기존 `KUKIE_CREDENTIAL_KEY`로 AES-GCM 암호화해 V26 테이블에 저장한다. 이 암호화 키의 보존·회전 정책은 기존 클러스터 자격증명과 같다.
4. 실제 도메인·인증서·방화벽·카운터 모델 설정은 고객/운영자가 준비해야 한다. 등록은 주소 정책을 검사할 뿐 연결 성공이나 GPU 준비를 보증하지 않는다. GET models 및 시험 호출로 확인한다.

| 메서드·경로 | 동작 |
| --- | --- |
| PUT `/clusters/{clusterId}/serving/endpoint` | 기존 Kukie 로그인으로 해당 ADMIN만 구성·교체. `{"baseUrl":"https://api.customer.example/v1","serviceToken":"<서비스 키>","models":["model-a"]}`. 204 |
| GET `/clusters/{clusterId}/serving/endpoint` | 해당 ADMIN에게 주소·모델만 반환. 미설정 시 200 빈 본문(클라이언트는 null로 정규화), 서비스 키/암호문은 반환하지 않음 |
| DELETE `/clusters/{clusterId}/serving/endpoint` | 해당 ADMIN만 연결 제거. 204. 연결 가능한 EKS는 자동 경로로 복귀, 그 외 대상은 이후 새 요청 503 |

외부 카운터 설정은 클러스터당 하나이며 외부 경로에서는 모델 이름을 그대로 전달한다. 서비스 키는 ASCII 가시문자 1~4096자(공백 없음), 모델은 1~100개, 모델명은 영숫자로 시작하고 영숫자·`.`·`_`·`/`·`-`로 최대 200자다. PUT은 전체 교체이며 모델·서비스 키를 모두 다시 보낸다. 연결 제거·교체 전에 이미 준비된 요청은 기존 연결로 완료할 수 있다.

HTTPS 443만 허용하고 base 경로는 `/v1`로 정규화한다. userinfo·query·fragment·다른 경로/포트는 거절한다. DNS 결과 중 하나라도 사설·loopback·link-local·metadata·CGNAT·multicast·예약/우회 주소이면 거절한다. 등록 시뿐 아니라 **실제 소켓 연결을 위한 DNS resolver에서 검증한 IP를 반환**하므로 검증 후 다른 DNS 해석으로 내부망에 접속하지 않는다. 연결 풀은 이미 공개 IP에 열린 소켓만 재사용한다. 원래 호스트의 TLS 인증서·이름 검증은 유지한다. 환경 프록시·redirect·자동 retry·쿠키 저장을 사용하지 않는다.

## 사용자 공통 API

백엔드 기준 base URL은 `/serving/v1`이다. 공개 도메인 및 프록시 라우팅은 배포에서 이 경로를 보존하도록 구성해야 한다. 아래 구현만으로 기존 `/v1` 주소가 자동 교체되는 것은 아니다.

| 메서드·경로 | 동작 |
| --- | --- |
| GET `/serving/v1/models` | 외부 카운터는 등록 모델 교집합, EKS 자동 경로는 오븐의 Ready 모델 목록. id/object/owned_by만 공개 |
| POST `/serving/v1/chat/completions` | OpenAI 호환 JSON을 해당 카운터에 전달. `model` 필수, `stream`은 생략/false 또는 true |

`Authorization: Bearer <Kukie 개인 키>`만 인정한다. 쿠키·로그인 JWT는 대체 인증이 아니다. 키에서 도출한 클러스터로만 라우팅하고 URL·헤더로 대상을 선택하지 않는다. 모든 신규 요청은 키·만료·폐기 및 API 이용 권한 ACTIVE를 확인한다. 사용자 요청 헤더는 전달하지 않고 카운터용 Authorization과 필요한 HTTP 콘텐츠 헤더만 새로 만든다. 사용자 키는 카운터로 가지 않는다.

요청 JSON은 최대 1 MiB. JSON object·문자열 model·boolean stream을 검사하고 중복 필드를 거절한다. model은 등록/발견 목록과 일치해야 한다. 나머지 messages/tools/생성 옵션 등은 상위 엔진이 검증한다. 외부 카운터에는 본문 바이트를 그대로, EKS에는 model만 바꾼 JSON을 전달한다. 지원하지 않는 query는 거절한다. embeddings/completions/files/responses 등 다른 경로는 지원하지 않는다.

일반 응답은 최대 8 MiB, SSE는 총 64 MiB다. SSE 바이트·줄바꿈·댓글·`[DONE]`은 변경 없이 chunk 단위로 flush한다. 자체 delta/error/heartbeat를 끼워 넣지 않는다. 따라서 카운터가 자체 heartbeat를 보내야 장시간 무출력 연결을 유지할 수 있다. SSE 시작 후 실패는 연결 종료로 알리며 HTTP 200을 뒤늦게 다른 상태로 바꿀 수 없다. 클라이언트는 `[DONE]` 없는 종료를 완료로 간주하면 안 된다.

오류는 `{"error":{"message":"…","type":"kukie_error","code":"…"}}` 형식이다. 상위 오류 본문·URL·서비스/EKS 인증정보는 반환하지 않는다.

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

기본값은 서버 인스턴스별 동시 32개, 키별 4개, 외부 카운터 연결 5초, 유휴 읽기 30초, 전체 중계 300초다. EKS 소켓 연결은 기존 EKS gateway의 3초 제한을 재사용하고 응답 헤더는 유휴 제한, 응답 본문은 별도 유휴 watchdog으로 제한한다. 설정은 `SERVING_RELAY_MAX_CONNECTIONS`, `SERVING_RELAY_MAX_CONNECTIONS_PER_KEY`, `SERVING_RELAY_CONNECT_SECONDS`, `SERVING_RELAY_IDLE_SECONDS`, `SERVING_RELAY_TOTAL_SECONDS`로 바꾼다. 전체 상한은 응답 헤더·본문 전송을 포함한다. 요청 본문 수신에는 배포 HTTP 서버의 업로드 시간 제한도 적용해야 한다. 여러 서버를 합친 전역 한도나 토큰 예산은 아직 구현하지 않았다.

요청마다 가상 스레드 하나를 쓰며 별도 deadline이 상위 요청을 cancel한다. Servlet async timeout은 추가 5초의 최종 정리 장치다. 클라이언트 연결 오류·write 실패도 상위를 cancel한다. 연결이 조용할 때 HTTP 서버가 클라이언트 단절을 즉시 감지하지 못할 수 있으므로 유휴/전체 상한이 정리를 보장한다. 실제 상위 작업이 종료될 때 슬롯을 반환한다. 카운터도 클라이언트 단절을 vLLM 취소로 연결해야 GPU 생성이 중단되며, 이 마지막 구간은 실제 카운터 종단 시험 대상이다.

권한/키 폐기 커밋 이후 시작된 인증은 거절한다. 이미 인증한 요청은 완료를 허용한다. DB 트랜잭션을 DNS·HTTP·스트리밍 중 유지하지 않는다.

## 사용량 계약과 데이터 처리

Kukie 서버를 프롬프트·응답이 통과한다. 고객 밖으로 데이터가 나가지 않는 구조가 아니다. 본문·개인 키·서비스 키·해시는 DB/감사 로그에 기록하지 않는다. 프록시/APM/Apache HTTP wire의 헤더·본문 로깅도 켜면 안 된다. 응답 캐시는 `no-store`, 버퍼링은 끈다. 외부 프록시에도 스트리밍/시간 제한 설정을 맞춰야 한다.

`ServingUsageObservation` 이벤트에 key/access/cluster/user ID, 모델, 시작 시각·소요 시간, 처리 결과·상태, 입출력 토큰 수와 usageMissing만 발행한다. 프롬프트·답변·원문 자격증명은 없다. 일반 JSON의 usage와 SSE의 한 줄 data JSON usage를 읽는다. SSE 이벤트 최대 64 KiB까지만 관찰하고 더 큰 줄은 버린다(전달은 유지). 토큰이 없거나 `[DONE]` 없이 끝나거나 실패하면 usageMissing이다. 스트리밍 사용량이 필요하면 호출자가 `stream_options.include_usage=true`를 설정해야 한다. 토큰이 없다고 0으로 추정하지 않는다.

DURO-197에서 시작 기록과 종료 이벤트를 PostgreSQL에 연결했다. [사용량 런북](serving-usage.md)의 90일 보관·중복/누락·재시작 미완료 정책을 따른다. 고객 카운터에는 서비스 키만 보이므로 사용자별 집계는 중앙의 키 식별 기록을 사용한다. 정확한 과금·토큰 예산은 아직 제공하지 않는다.

## 검증·배포·롤백

가짜 회사 A/B 카운터의 키·라우팅 격리, 원형 SSE, 본문 없는 usage, 취소·timeout·슬롯 반환을 통합 시험했다. 실제 HTTP 클라이언트의 스트림 종료와 504, 실제 로컬 HTTPS 소켓의 인증서 이름·DNS 주입·redirect 차단·읽기 timeout·cancel도 시험한다. `src/test/resources/serving/counter-test.p12`는 공개된 test-only 비밀번호의 테스트 인증서이며 운영 신뢰 저장소에는 추가하지 않는다.

전체 서버 build와 보안 annotation convention 검사를 수행한다. 기존 키/사용량 스키마 외 EKS 자동 연결용 추가 migration은 없다. 운영 반영에는 공통 공개 경로·TLS·프록시 및 EKS 접속·권한·오븐 모델 준비 확인이 필요하다. 외부 카운터 경로에만 호스트 허용 목록과 서비스 키 등록이 추가로 필요하다. GPU를 켜야 하는 실제 추론 검증은 별도 운영 단계다. 롤백은 이전 앱/공개 라우팅으로 되돌리고 추가 테이블을 보존한다. 서비스 키 유출 시 고객 카운터에서 폐기·재발급한 뒤 PUT으로 교체한다.
