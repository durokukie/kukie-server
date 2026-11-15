package com.duro.kukie.cluster

import com.duro.kukie.chat.ChatFixture
import com.duro.kukie.chat.domain.ChatRunRepository
import com.duro.kukie.chat.domain.ChatSessionRepository
import com.duro.kukie.chat.domain.lockByIdOrThrow
import com.duro.kukie.cluster.application.DeleteClusterService
import com.duro.kukie.cluster.domain.Cluster
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.cluster.exception.ClusterHasActiveRunException
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.team.domain.TeamRole
import com.duro.kukie.user.UserFixture
import com.duro.kukie.user.domain.User
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForObject
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ClusterDeleteConcurrencyTest : IntegrationTest() {

    @Autowired
    private lateinit var clusterRepository: ClusterRepository

    @Autowired
    private lateinit var chatSessionRepository: ChatSessionRepository

    @Autowired
    private lateinit var chatRunRepository: ChatRunRepository

    @Autowired
    private lateinit var deleteClusterService: DeleteClusterService

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `대화를 만드는 중에 대화까지 지우면 그 대화도 함께 지운다`() {
        // given
        val cluster = savedCluster()

        // when
        val deletion = deleteClusterWhile(cluster) {
            chatSessionRepository.saveAndFlush(ChatFixture.session(owner.id, cluster.id))
        }

        // then
        deletion.isSuccess shouldBe true
        clusterRepository.existsById(cluster.id) shouldBe false
        chatSessionRepository.count() shouldBe 0
    }

    @Test
    fun `요청을 시작하는 중에 대화까지 지우려 하면 아무것도 지우지 않는다`() {
        // given
        val cluster = savedCluster()
        val session = chatSessionRepository.save(ChatFixture.session(owner.id, cluster.id))

        // when
        val deletion = deleteClusterWhile(cluster) {
            chatSessionRepository.lockByIdOrThrow(session.id)
            chatRunRepository.saveAndFlush(ChatFixture.run(session.id))
        }

        // then
        deletion.exceptionOrNull().shouldBeInstanceOf<ClusterHasActiveRunException>()
        clusterRepository.existsById(cluster.id) shouldBe true
        chatSessionRepository.existsById(session.id) shouldBe true
        chatRunRepository.count() shouldBe 1
    }

    private fun deleteClusterWhile(cluster: Cluster, hold: () -> Unit): Result<Unit> {
        val transaction = TransactionTemplate(transactionManager)
        val holding = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val holder = executor.submit {
                transaction.executeWithoutResult {
                    hold()
                    holding.countDown()
                    proceed.await(10, TimeUnit.SECONDS)
                }
            }
            holding.await(5, TimeUnit.SECONDS) shouldBe true

            val deletion = executor.submit<Result<Unit>> {
                runCatching { deleteClusterService(cluster.id, owner.id, deleteConversations = true) }
            }
            awaitLockWait()
            proceed.countDown()

            holder.get(10, TimeUnit.SECONDS)
            return deletion.get(10, TimeUnit.SECONDS)
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
        error("클러스터 삭제가 잠금을 기다리지 않았다")
    }

    private fun savedCluster(): Cluster =
        clusterRepository.save(ClusterFixture.cluster(registeredBy = owner.id, teamId = savedTeam(owner.id to TeamRole.ADMIN)))

    private val owner: User by lazy { userRepository.save(UserFixture.user()) }
}
