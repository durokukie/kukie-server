package com.duro.kukie.auth

import com.duro.kukie.auth.application.LinkGitHubAccountService
import com.duro.kukie.auth.application.port.`in`.LinkGitHubAccountCommand
import com.duro.kukie.auth.exception.GitHubAlreadyLinkedException
import com.duro.kukie.support.FakeOAuthClient
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.user.UserFixture
import com.duro.kukie.user.domain.GitHubAccount
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
    private lateinit var fakeOAuthClient: FakeOAuthClient

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `한 회원에 서로 다른 GitHub 계정을 동시에 연결하면 먼저 끝난 연결만 남는다`() {
        // given
        val user = userRepository.save(UserFixture.oAuthUser())
        val first = GitHubAccount(id = 1, login = "first")
        fakeOAuthClient.profile = FakeOAuthClient.DEFAULT_PROFILE.copy(gitHub = GitHubAccount(id = 2, login = "second"))
        val holding = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        // when
        val second = try {
            val holder = executor.submit {
                TransactionTemplate(transactionManager).executeWithoutResult {
                    userRepository.lockByIdOrThrow(user.id).linkGitHub(first)
                    holding.countDown()
                    proceed.await(10, TimeUnit.SECONDS)
                }
            }
            holding.await(5, TimeUnit.SECONDS) shouldBe true
            val linking = executor.submit<Result<*>> {
                runCatching { linkGitHubAccountService(LinkGitHubAccountCommand(user.id, "code", "http://127.0.0.1:3000/callback", "verifier")) }
            }
            awaitLockWait()
            proceed.countDown()
            holder.get(10, TimeUnit.SECONDS)
            linking.get(10, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
        }

        // then
        second.exceptionOrNull().shouldBeInstanceOf<GitHubAlreadyLinkedException>()
        userRepository.findByIdOrNull(user.id).shouldNotBeNull().githubId shouldBe first.id
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
}
