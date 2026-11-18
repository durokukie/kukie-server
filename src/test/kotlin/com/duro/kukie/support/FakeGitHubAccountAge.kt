package com.duro.kukie.support

import com.duro.kukie.hosting.application.port.out.GitHubAccountAge
import com.duro.kukie.hosting.application.port.out.GitHubAccountNow
import com.duro.kukie.hosting.exception.HostingUnavailableException
import java.time.Instant

class FakeGitHubAccountAge : GitHubAccountAge, Resettable {

    var login = "recklehs"
    var createdAt: Instant? = OLD

    override fun read(githubId: Long): GitHubAccountNow = GitHubAccountNow(login, createdAt ?: throw HostingUnavailableException())

    override fun clear() {
        login = "recklehs"
        createdAt = OLD
    }

    companion object {
        val OLD: Instant = Instant.parse("2020-01-01T00:00:00Z")
    }
}
