package com.duro.kukie.team.domain

enum class TeamRole(val level: Int) {
    MEMBER(1), // 팀·클러스터 관리 권한 없음. readOnly
    ADMIN(2), // 팀·클러스터 관리와 모든 Kubernetes 작업 권한. 팀에 최소 1명은 있어야 한다.
}
