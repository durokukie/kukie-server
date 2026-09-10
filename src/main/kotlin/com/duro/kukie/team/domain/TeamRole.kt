package com.duro.kukie.team.domain

/**
 * 팀 안에서의 역할. 제품기획서 02·03 기준.
 *
 * - [ADMIN] 팀·클러스터 관리와 모든 Kubernetes 작업 권한. 팀에 최소 1명은 있어야 한다.
 * - [MEMBER] 팀 관리 권한 없음. 클러스터별 Viewer/Operator 권한은 후속 범위.
 */
enum class TeamRole {
    ADMIN,
    MEMBER,
    ;

    val isAdmin: Boolean
        get() = this == ADMIN
}
