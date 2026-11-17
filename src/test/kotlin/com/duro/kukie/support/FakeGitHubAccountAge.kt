package com.duro.kukie.support

import com.duro.kukie.hosting.application.port.out.GitHubAccountAge
import com.duro.kukie.hosting.exception.HostingUnavailableException
import java.time.Instant

class FakeGitHubAccountAge : GitHubAccountAge, Resettable {

    var createdAt: Instant? = OLD

    override fun createdAt(githubId: Long): Instant = createdAt ?: throw HostingUnavailableException()

    override fun clear() {
        createdAt = OLD
    }

    companion object {
        val OLD: Instant = Instant.parse("2020-01-01T00:00:00Z")
    }
}
