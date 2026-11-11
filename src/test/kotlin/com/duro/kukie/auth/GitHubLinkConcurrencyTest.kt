package com.duro.kukie.auth

import com.duro.kukie.auth.application.LinkGitHubAccountService
import com.duro.kukie.auth.application.OAuthLogInService
import com.duro.kukie.auth.application.port.`in`.LinkGitHubAccountCommand
import com.duro.kukie.auth.application.port.`in`.OAuthLogInCommand
import com.duro.kukie.auth.domain.OAuthProvider
import com.duro.kukie.auth.exception.GitHubAlreadyLinkedException
import com.duro.kukie.support.FakeOAuthClient
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.user.UserFixture
import com.duro.kukie.user.domain.GitHubAccount
import com.duro.kukie.user.domain.User
import com.duro.kukie.user.domain.lockByIdOrThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForObject
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class GitHubLinkConcurrencyTest : IntegrationTest() {

    @Autowired
    private lateinit var linkGitHubAccountService: LinkGitHubAccountService

    @Autowired
    private lateinit var oAuthLogInService: OAuthLogInService

    @Autowired
    private lateinit var fakeOAuthClient: FakeOAuthClient

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `한 회원에 서로 다른 GitHub 계정을 동시에 연결하면 먼저 끝난 연결만 남는다`() {
        // given
        val user = userRepository.save(UserFixture.oAuthUser())
        fakeOAuthClient.profile = FakeOAuthClient.DEFAULT_PROFILE.copy(gitHub = SECOND)

        // when
        val second = whileLinkingFirst(user) {
            linkGitHubAccountService(LinkGitHubAccountCommand(user.id, "code", "http://127.0.0.1:3000/callback", "verifier"))
        }

        // then
        second.exceptionOrNull().shouldBeInstanceOf<GitHubAlreadyLinkedException>()
        userRepository.findByIdOrNull(user.id).shouldNotBeNull().githubId shouldBe FIRST.id
    }

    @Test
    fun `연결하는 중에 다른 GitHub 계정으로 로그인해도 연결한 계정이 남는다`() {
        // given
        val user = userRepository.save(UserFixture.oAuthUser())
        fakeOAuthClient.profile = FakeOAuthClient.DEFAULT_PROFILE.copy(email = user.email.value, gitHub = SECOND)

        // when
        val logIn = whileLinkingFirst(user) {
            oAuthLogInService(OAuthLogInCommand(OAuthProvider.GITHUB, "code", "http://127.0.0.1:3000/callback", null))
        }

        // then
        logIn.isSuccess shouldBe true
        userRepository.findByIdOrNull(user.id).shouldNotBeNull().githubId shouldBe FIRST.id
    }

    @Test
    fun `연결이 실패하면 다른 회원에 있던 연결은 그대로 남는다`() {
        // given
        val gitHub = GitHubAccount(id = 583231, login = "octocat")
        val previous = userRepository.save(UserFixture.oAuthUser(email = "previous@example.com").apply { linkGitHub(gitHub) })
        val user = userRepository.save(UserFixture.oAuthUser())
        fakeOAuthClient.profile = FakeOAuthClient.DEFAULT_PROFILE.copy(gitHub = gitHub.copy(login = "rejected"))
        jdbcTemplate.execute("alter table tbl_user add constraint reject_login check (github_login <> 'rejected')")

        // when
        val result = try {
            runCatching {
                linkGitHubAccountService(LinkGitHubAccountCommand(user.id, "code", "http://127.0.0.1:3000/callback", "verifier"))
            }
        } finally {
            jdbcTemplate.execute("alter table tbl_user drop constraint reject_login")
        }

        // then
        result.isFailure shouldBe true
        userRepository.findByIdOrNull(previous.id).shouldNotBeNull().githubId shouldBe gitHub.id
        userRepository.findByIdOrNull(user.id).shouldNotBeNull().githubId shouldBe null
    }

    /** 다른 요청이 회원 행을 잠그고 첫 계정을 연결하는 동안 action 을 시작하고, 그 연결이 커밋된 뒤의 결과를 준다. */
    private fun whileLinkingFirst(user: User, action: () -> Any): Result<Any> {
        val holding = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val holder = executor.submit {
                TransactionTemplate(transactionManager).executeWithoutResult {
                    userRepository.lockByIdOrThrow(user.id).linkGitHub(FIRST)
                    holding.countDown()
                    proceed.await(10, TimeUnit.SECONDS)
                }
            }
            holding.await(5, TimeUnit.SECONDS) shouldBe true
            val acting = executor.submit<Result<Any>> { runCatching(action) }
            awaitLockWait()
            proceed.countDown()
            holder.get(10, TimeUnit.SECONDS)
            return acting.get(10, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
        }
    }

    private fun awaitLockWait() {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            val waiting = jdbcTemplate.queryForObject<Boolean>(
                "select exists(select 1 from pg_stat_activity where datname = current_database() and wait_event_type = 'Lock')",
            )
            if (waiting == true) return
            Thread.sleep(10)
        }
        error("연결 요청이 회원 잠금을 기다리지 않았다")
    }

    companion object {
        private val FIRST = GitHubAccount(id = 1, login = "first")
        private val SECOND = GitHubAccount(id = 2, login = "second")
    }
}
