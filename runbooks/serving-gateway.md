# Go 게이트웨이 내부 API

Go 저장소: `durokukie/kukie-serving-gateway`. 계약은 해당 저장소 `docs/control-api.md`를 따른다. `/internal/serving-gateway`는 관리/사용량 전용이며 모델 본문은 받지 않는다.

- `KUKIE_GATEWAY_INTERNAL_TOKEN`을 전용 자격으로 설정한다. 기본값이 비어 있으면 전부 거절한다. 기존 `KUKIE_INTERNAL_TOKEN`과 별도로 관리한다.
- 내부 ingress/네트워크에서 게이트웨이에만 HTTPS 접근을 허용한다. 기존 공용 API 라우팅에 `/internal`을 추가하지 않는다.
- 게이트웨이 전용 내부 자격 교체는 서버/게이트웨이에서 조율한다. 다중 키 무중단 회전은 후속이다.
- 옛 키별 API `/authorize`·`/requests`·`/requests/{requestId}/finish`는 삭제됐다(DURO-223). 게이트웨이는 아래 `/key-snapshot`과 `/usage`만 쓴다.
- `/key-snapshot`(DURO-223)은 지금 쓸 수 있는 키(폐기·만료되지 않고 이용 권한이 ACTIVE인 키)의 해시·keyId·clusterId·만료와 클러스터별 허용 모델을 한 번에 준다. 키 원문은 저장하지 않으므로 응답에도 없다. 50,000개를 넘으면 500(`SERVING_KEY_SNAPSHOT_TOO_LARGE`)으로 거절한다.
- `/usage`(DURO-223)는 끝난 요청 `records`를 최대 500건 한 번에 받는다. requestId가 행 ID라 재전송해도 한 행만 남는다. 폐기·만료된 키의 기록도 저장하며, 키가 없거나 시작 시각이 1일 전보다 오래됐거나 5분 넘게 미래면 그 건만 건너뛴다.
  - 레코드의 `ttfbMillis`(DURO-226)는 첫 바이트까지 걸린 밀리초다. 0 이상 정수 또는 null이며, 옛 게이트웨이처럼 빠져도 받는다.
  - 같은 본문의 `rejections`(DURO-226)는 게이트웨이가 중계 전에 거절한 요청을 키·모델·사유·분 단위로 센 것이다. 각 항목은 `{id, keyId, model(문자열 또는 null, 최대 200자), reason, minute(RFC3339 UTC), count(1 이상)}`이고 최대 500건이다. 없으면 빠져도 된다. 거절만 보낼 때는 `records`를 빈 배열로 보낸다. 둘 다 비면 400이다.
  - reason은 `concurrency_limit`·`connector_busy`·`model_not_allowed`·`invalid_request`·`request_too_large`·`upstream_unavailable` 중 하나다. 그 밖의 값이 하나라도 있으면 배치 전체를 400으로 거절한다. minute는 서버가 분 단위로 자른다.
  - 거절도 id가 행 ID라 재전송해도 한 행만 남고, 키가 없거나 minute가 사용량과 같은 허용 시간 밖이면 그 건만 건너뛴다.
- 현재 모델 목록은 기존 ServingEndpoint 설정만 사용한다. 커넥터의 모델 등록과 EKS 자동 발견 통합은 이 변경에 포함하지 않는다.

기존 Kotlin 중계·EKS 작업과 분리된 내부 계약 추가다. 이 API를 배포하는 것만으로 Go 게이트웨이·커넥터가 실행되거나 공통 URL 라우팅이 바뀌지 않는다. 실제 GPU와 운영 변경은 수행하지 않았다.

## 커넥터 등록

클러스터 관리자는 `POST /clusters/{clusterId}/serving/connector-credential`에 models 목록으로 등록/회전한다. 원문 256비트 hex 자격은 생성 응답에서 한 번만 반환한다. V28의 tbl_connector_credential에는 SHA-256, 모델, UTC 만료(24시간)만 저장한다. DELETE는 멱등 폐기다. 일반 API 이용자에게 이 기능을 허용하지 않는다.

게이트웨이는 같은 전용 내부 자격으로 `POST /internal/serving-gateway/connector-bindings`를 조회해 활성 해시/만료 스냅샷만 받는다. 신규 발급은 활성 등록 256개 한계에서 429로 거절하며 기존 등록 회전은 허용한다. 내부 스냅샷이 이 한계를 넘는 비정상 상태는 500으로 거절한다. 조회/적용 실패 시 게이트웨이가 세션을 폐기하므로 control API 장애가 추론 중단으로 이어질 수 있다. 5초 조회 주기+5초 요청 제한으로 회전/폐기는 보통 최대 10초 내 반영하며 해당 커넥터의 진행 요청도 종료한다.

커넥터 등록이 존재하면 새 Go 경로의 모델 허용 목록은 그 등록을 따른다. 만료/폐기 상태에서 기존 외부 endpoint로 묵시적 fallback하지 않는다. 기존 Kotlin 공개 중계 라우팅은 변경하지 않는다. 원클릭 설치는 DURO-203에서 연결한다.

### 커넥터 자격 자동 갱신

커넥터는 게이트웨이를 거쳐 `POST /internal/serving-gateway/connector-credential/renew`(`{clusterId, tokenSha256}`)로 자격을 스스로 바꾼다. 게이트웨이가 원문을 먼저 확인하고 해시만 넘긴다. 응답은 새 원문과 만료(`{token, expiresAt}`)뿐이며 `no-store`다.

- 현재 자격으로 갱신하면 새 자격(수명 `serving.connector-credential.lifetime`, 기본 24시간)을 만들고, 현재 자격은 직전 자격이 되어 겹침 기간(`overlap`, 기본 30분, 원래 만료를 넘지 않음) 동안만 유효하다.
- 겹침 안의 직전 자격으로 다시 오면 앞선 응답을 잃은 것으로 보고 새 자격을 다시 준다. 직전 자격 만료는 늘리지 않는다.
- 클러스터마다 `renew-min-interval`(기본 30초) 안의 두 번째 회전은 429 `SERVING_CONNECTOR_RENEW_TOO_SOON`이다. 모르는·만료된 자격은 401 `SERVING_CONNECTOR_CREDENTIAL_INVALID`다.
- 관리자 재발급과 폐기는 직전 자격도 지운다. `connector-bindings`는 겹침 안의 직전 해시·만료(`previousTokenSha256`, `previousExpiresAt`)도 준다. 구 게이트웨이는 이 필드를 무시한다.
- V31이 `previous_token_hash`, `previous_expires_at`, `rotated_at`를 더한다. 로컬 시험은 `KUKIE_CONNECTOR_CREDENTIAL_LIFETIME`, `KUKIE_CONNECTOR_CREDENTIAL_OVERLAP`, `KUKIE_CONNECTOR_RENEW_MIN_INTERVAL`로 줄인다.
- 배포 순서는 서버 → 게이트웨이 → 커넥터 → 설치 매니페스트다.


## 모델 상태 안내 (DURO-205)

전용 내부 POST /internal/serving-gateway/model-status로 최대 256개 상태를 보고한다. 현재 커넥터 자격 해시/만료가 맞는 행만 V29에 저장한다. 원문 키·내부 주소는 저장하지 않는다. 기존 사용자/관리자 info 권한을 그대로 적용하고 허용 모델만 AVAILABLE/UNAVAILABLE/UNKNOWN으로 반환한다.

모델 확인 시각과 보고 수신 시각은 각각 45초 유효하다. 자격 회전·폐기/만료·클러스터 삭제·오래된 보고는 준비 상태를 유지하지 못한다. 중앙 서버를 재시작해도 PostgreSQL의 시간 제한을 적용하며, 구형 카운터/커넥터와 HTTPS 직접 경로는 준비 상태를 추정하지 않는다. Go 게이트웨이 /models는 연결된 최신 관측과 키 허용 목록의 교집합만 제공한다. 기존 Kotlin 공개 중계 경로는 이번 변경으로 Go에 전환되지 않는다.

배포 순서: 서버 V29/수신 API → 게이트웨이 v1+v2 지원 → 새 카운터·커넥터 → 화면. 설치/인증서/공개 라우팅은 DURO-203/204 범위다. 단일 게이트웨이에서 보고하며 여러 게이트웨이의 상태 병합은 아직 없다.

info의 modelStatusValidForSeconds는 서버가 계산한 남은 관측 유효 시간(0~45)이다. 화면은 자신의 절대 시계가 아니라 응답 수신 후 경과 시간으로 이 값을 소진한다. 고객 시계는 ageMs를 통해 게이트웨이에서 정규화하며, 게이트웨이와 중앙 서버의 시각은 같은 플랫폼에서 관리한다.

## 모델별 API 공개 설정

V30은 클러스터별 차단 모델을 별도로 저장한다. 기본은 커넥터 등록 모델을 모두 허용한다. GET /clusters/{clusterId}/serving/model-access는 등록된 모델과 enabled를 반환하고 PATCH는 {"model":"model-id","enabled":false}로 한 모델을 변경한다. 클러스터 관리자만 접근할 수 있으며 미등록 모델은 400, 커넥터 미등록 변경은 503이다. 모든 모델 차단도 허용한다.

차단은 새 요청에 적용된다. `/key-snapshot`의 클러스터별 허용 모델에서 차단 모델을 빼므로, 게이트웨이는 다음 스냅샷을 받은 뒤 그 모델 요청을 거절한다. 사용자 info의 models/modelStatuses에서는 제외하며 관리자 info에는 enabled=false로 남겨 재허용할 수 있다. 진행 중인 스트림은 중단하지 않고 GPU 파드/노드도 변경하지 않는다. 커넥터 연결 자격 회전은 차단 정책을 변경하지 않는다. 삭제된 클러스터의 정책은 FK cascade로 정리된다.

서버·V30을 먼저 배포한 뒤 프론트 모델 공개 화면을 배포한다. 운영 클러스터의 모델이나 키 설정을 이 기능의 테스트 목적으로 임의 변경하지 않는다. 로컬 PostgreSQL/Redis 통합 검증은 권한 경계·미등록 모델·전체 차단·재허용·자격 회전·게이트웨이 거절을 포함한다.

등록에서 빠진 모델의 차단 정책도 유지한다. 같은 모델 ID를 다시 등록하면 기존 차단이 계속 적용되며, 관리자가 명시적으로 재허용해야 한다.
