package com.duro.kukie.gitops.application.port.`in`

data class GitHubSetupCommand(
    val installationId: Long?,
    val setupAction: String?,
    val state: String?,
    val code: String?,
)
