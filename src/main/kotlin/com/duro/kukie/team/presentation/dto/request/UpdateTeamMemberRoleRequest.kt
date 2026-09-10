package com.duro.kukie.team.presentation.dto.request

import com.duro.kukie.team.domain.TeamRole

/**
 * `role` 은 nullable 이 아니라 Bean Validation 이 돌 기회가 없다 — 빠졌거나 없는 값이면 Jackson 이
 * 먼저 던지고 `GlobalExceptionHandler` 가 400 으로 바꾼다. 그래서 `@NotNull` 을 붙이지 않는다.
 */
data class UpdateTeamMemberRoleRequest(
    val role: TeamRole,
)
