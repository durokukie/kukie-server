# Go 게이트웨이 내부 API

Go 저장소: `durokukie/kukie-serving-gateway`. 계약은 해당 저장소 `docs/control-api.md`를 따른다. `/internal/serving-gateway`는 관리/사용량 전용이며 모델 본문은 받지 않는다.

- `KUKIE_GATEWAY_INTERNAL_TOKEN`을 전용 자격으로 설정한다. 기본값이 비어 있으면 전부 거절한다. 기존 `KUKIE_INTERNAL_TOKEN`과 별도로 관리한다.
- 내부 ingress/네트워크에서 게이트웨이에만 HTTPS 접근을 허용한다. 기존 공용 API 라우팅에 `/internal`을 추가하지 않는다.
- 자격 교체는 서버/게이트웨이에서 조율한다. 다중 키 무중단 회전은 후속이다.
- `/authorize`는 개인 키를 확인하고 keyId/clusterId/허용 모델만 반환한다. 주소·카운터 자격을 반환하지 않는다.
- `/requests`는 키 ID의 현재 상태와 허용 모델을 재확인하고 시작 기록을 확정한다. 반환에 실패하면 게이트웨이는 모델을 호출하지 않는다.
- 완료 기록은 저장된 요청 ID로 본래 대상을 결정하며 기존 조건부 writer를 재사용한다. 키 폐기 전 시작된 요청도 완료 가능하다. 미완료 시작 행은 기존 유지 작업이 INTERRUPTED로 처리한다.
- 현재 모델 목록은 기존 ServingEndpoint 설정만 사용한다. 커넥터의 모델 등록과 EKS 자동 발견 통합은 이 변경에 포함하지 않는다.

기존 Kotlin 중계·EKS 작업과 분리된 내부 계약 추가다. 이 API를 배포하는 것만으로 Go 게이트웨이·커넥터가 실행되거나 공통 URL 라우팅이 바뀌지 않는다. 실제 GPU와 운영 변경은 수행하지 않았다.
