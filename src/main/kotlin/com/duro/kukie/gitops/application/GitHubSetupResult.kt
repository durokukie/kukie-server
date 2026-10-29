package com.duro.kukie.gitops.application

import java.util.UUID

data class GitHubSetupResult(
    val outcome: Outcome,
    /** state 없이 온 설정 변경이면 null — 누가 보낸 요청인지 모르므로 팀을 알려 주지 않는다. */
    val teamId: UUID?,
) {
    enum class Outcome(val value: String) {
        INSTALLED("installed"),
        UPDATED("updated"),
        REQUESTED("requested"),
    }
}
