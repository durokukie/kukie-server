package com.duro.kukie.hosting.application.port.out

import java.time.Instant

/** GitHub 이 지금 알고 있는 계정: 계정명(바뀔 수 있다)과 만든 때. 가입 조건을 보는 데 쓴다. */
interface GitHubAccountAge {

    /** GitHub 이 답하지 않거나 계정이 없으면 HostingUnavailableException. */
    fun read(githubId: Long): GitHubAccountNow
}

data class GitHubAccountNow(val login: String, val createdAt: Instant)
