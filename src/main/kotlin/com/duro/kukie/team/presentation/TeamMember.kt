package com.duro.kukie.team.presentation

import com.duro.kukie.team.domain.TeamRole

/** `{teamId}` 팀에서 이 역할 이상이어야 부를 수 있는 핸들러. [TeamRoleInterceptor] 가 핸들러 앞에서 검사한다. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class TeamMember(val role: TeamRole = TeamRole.MEMBER)
