package com.duro.kukie.gitops.application

import java.util.UUID

data class GitHubSetupResult(
    val outcome: Outcome,
    val teamId: UUID,
) {
    enum class Outcome(val value: String) {
        INSTALLED("installed"),
        UPDATED("updated"),
        REQUESTED("requested"),
    }
}
