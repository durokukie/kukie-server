# API 이용 권한 (DURO-194)

현재 단계는 기존 회원 기반 API 이용 초대·수락·조회·취소다. 키 발급(DURO-195), 카운터 중계(DURO-196), 화면·사용량(DURO-197)은 아직 이 변경에 포함하지 않는다. DB 마이그레이션 V24가 필요하며 실제 배포는 별도다.

## 권한

- 클러스터의 기존 팀 ADMIN이 API 이용자를 관리한다. 일반 팀원이나 API 이용 권한만 있는 사람은 관리자가 아니다.
- API 이용 초대는 팀 가입을 만들지 않는다. 같은 계정이 A·B에서 각각 이용 권한을 받을 수 있다.
- 이메일로 초대한다. 미가입 주소도 기록할 수 있으며, 해당 이메일로 기존 회원가입·로그인을 완료한 본인만 수락한다.
- 초대는 UTC 기준 7일 유효하다. 수락한 이용 권한 자체가 7일 후 만료되는 것은 아니다.
- 첫 단계는 전용 초대 조회 API로 전달한다. 초대 이메일 발송과 기존 Inbox 화면 통합은 구현하지 않았다.
- 계정의 허용 대상 조회 응답에는 표시 이름과 ID만 넣고 kubeconfig·API 서버 주소·팀 멤버·타 사용자 정보는 넣지 않는다.

## API

모든 요청은 기존 Kukie 로그인 인증을 사용한다. API 키 인증 경로가 아니다.

| 메서드·경로 | 동작 | 권한 |
| --- | --- | --- |
| POST `/clusters/{clusterId}/serving/invitations` | `{"email":"person@example.com"}`로 초대 생성, 201 | 해당 클러스터 관리자 |
| GET `/clusters/{clusterId}/serving/accesses?page=0&size=20` | 과거 상태를 포함한 이용 권한 목록, size 1~100 | 해당 클러스터 관리자 |
| DELETE `/clusters/{clusterId}/serving/accesses/{accessId}` | PENDING 초대 또는 ACTIVE 이용 권한 취소, 204 | 해당 클러스터 관리자 |
| GET `/serving/invitations` | 내 이메일로 온 유효한 대기 초대 | 로그인 사용자 |
| POST `/serving/invitations/{accessId}/accept` | 본인 초대 수락, 204 | 초대받은 이메일 소유자 |
| POST `/serving/invitations/{accessId}/decline` | 본인 초대 거절, 204 | 초대받은 이메일 소유자 |
| GET `/serving/clusters` | 내가 수락한 ACTIVE 제공 클러스터 | 로그인 사용자 |

관리자 목록은 DB 페이지로 조회한다. 본인 목록은 해당 사용자 권한만 반환한다. 존재하지 않거나 타인의 초대는 404, 유효 기간이 지난 초대 수락은 410, 중복 초대·이미 처리한 초대는 409다. 일반 팀원 관리 요청은 403, 다른 팀 클러스터 관리 요청은 기존 클러스터 정책에 따라 404다.

## 저장·동시성·키 단계 계약

`tbl_serving_access`만 추가하며 회원은 `tbl_user`를 재사용한다. 상태는 PENDING → ACTIVE / DECLINED / REVOKED / EXPIRED이며 ACTIVE → REVOKED만 가능하다. REVOKED를 다시 ACTIVE로 바꾸지 않는다. 재초대는 새 행·새 권한 ID를 사용한다.

클러스터 행 잠금으로 같은 클러스터의 초대·수락·취소·삭제를 직렬화한다. 부분 unique index로 같은 클러스터·이메일에 유효 상태(PENDING/ACTIVE)가 하나만 존재하도록 한다. 클러스터 삭제는 외래키 CASCADE로 초대·권한을 함께 제거한다.

DURO-195에서 키를 `(accessId, userId)`에 귀속하고 매 신규 요청에 `ServingAccessPermission.requireActive(accessId, userId)`를 검사해야 한다. 이전 권한 ID에 묶인 키는 재초대·재수락 후에도 사용할 수 없다. 이 검사는 권한 계층에서 검증했으며 실제 키 발급·키 인증·진행 중 추론 취소는 아직 구현하지 않았다.

개인 키·프롬프트·응답은 이 단계에서 다루거나 저장하지 않는다. 별도 외부 연결·GPU 기동·클러스터 변경도 하지 않는다.
