package com.duro.kukie.hosting.application.port.out

import java.time.Instant

/** GitHub 계정을 만든 때. 가입한 지 얼마 안 된 계정을 가리는 데 쓴다. */
interface GitHubAccountAge {

    /** GitHub 이 답하지 않거나 계정이 없으면 HostingUnavailableException. */
    fun createdAt(githubId: Long): Instant
}
