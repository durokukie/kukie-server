# EKS 공통 API 자동 연결 확인

## 사용 흐름

Kukie에 이미 FULL 권한으로 연결한 EKS → GPU 화면에서 오븐 모델 준비 → 기존 키 관리에서 이용자 초대 → 이용자가 내 API에서 수락·키 발급 → 표시된 공통 API 주소로 호출.

- `EKS 자동 연결`이면 카운터 주소/별도 서비스 키를 입력하지 않는다.
- 내 API의 제공 모델 ID를 그대로 요청한다. `gpt-oss-20b`는 내부적으로 `openai/gpt-oss-20b`로 변환된다.
- 모델이 없으면 준비된 모델 없음으로 안내한다. API 호출로 GPU/모델을 기동하지 않는다.
- API 이용자는 팀 ADMIN으로 승격되지 않는다. 키에 연결된 클러스터의 모델만 요청한다.
- 명시적 외부 카운터 설정이 있으면 그것을 먼저 사용한다. 자동 연결로 전환하려면 관리자가 외부 설정을 해제한다. 이때 외부 모델명과 자동 발견 모델 ID가 다를 수 있다.

## GPU 없이 확인할 수 있는 범위

1. 관리자/이용자 info의 connectionMode, configured, models를 확인한다. EKS_API 주소·CA·AWS 계정 정보는 응답에 없어야 한다.
2. 개인 키로 `/serving/v1/models`를 조회한다. 모델이 없으면 200 빈 목록이며 이는 연결 오류가 아니다.
3. 등록된 오븐 모델이 없을 때 chat의 503 `SERVING_MODEL_UNAVAILABLE`을 확인한다. 모르는 모델 이름은 다른 모델로 우회하지 않는다.
4. 키 폐기·권한 취소 후 `/models`는 401이어야 한다.

실제 GPU 기동은 이 절차에 포함하지 않는다. 사용자 승인 없이 기존 모델을 기동·교체·종료하지 않는다.

## 로컬 종단 검증

`EksServingEndToEndTest`는 실제 Spring HTTP·PostgreSQL·EKS gateway를 로컬 가짜 Kubernetes API와 연결한다. STS만 test-only 응답으로 대체하며 실제 AWS 계정/GPU/키를 사용하지 않는다.

확인 대상: 외부 endpoint 미설정 상태의 models/일반 JSON/원형 SSE, 모델 ID 변환, 개인 키 대신 EKS 인증·쿠키 차단, 비팀 API 이용자의 관리 API 차단, 키 폐기, Loading/삭제/CRD 없음, 제한된 AWS 연결, 목록 403/잘림, 429/503/redirect/idle timeout, 동시 상한·클라이언트 취소·총 시간 제한 및 DB 사용량.

로컬 fake gateway만 API 서버 주소를 loopback HTTP로 바꾼다. 운영 gateway의 등록된 주소·CA 사용과 인증서 이름 검증을 완화하지 않는다. 공개 CloudFront/ALB 경유의 긴 스트리밍, 실제 vLLM 취소, private-only EKS 네트워크는 별도 운영 검증으로 남긴다.

설계·한도·외부 카운터 호환은 [중계 런북](serving-relay.md), 보관·누락은 [사용량 런북](serving-usage.md)을 따른다.
