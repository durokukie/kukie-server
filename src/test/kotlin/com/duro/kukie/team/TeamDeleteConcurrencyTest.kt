package com.duro.kukie.team

import com.duro.kukie.actionplan.ActionPlanFixture
import com.duro.kukie.actionplan.domain.ActionPlanRepository
import com.duro.kukie.actionplan.domain.closeOpenPlans
import com.duro.kukie.chat.ChatFixture
import com.duro.kukie.chat.domain.ChatRun
import com.duro.kukie.chat.domain.ChatRunRepository
import com.duro.kukie.chat.domain.ChatSession
import com.duro.kukie.chat.domain.ChatSessionRepository
import com.duro.kukie.chat.domain.ChatVisibility
import com.duro.kukie.chat.domain.lockByIdOrThrow
import com.duro.kukie.cluster.ClusterFixture
import com.duro.kukie.cluster.domain.Cluster
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.team.application.DeleteTeamService
import com.duro.kukie.team.domain.Team
import com.duro.kukie.team.domain.TeamMembershipRepository
import com.duro.kukie.team.domain.TeamRepository
import com.duro.kukie.user.UserFixture
import com.duro.kukie.user.domain.User
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForObject
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class TeamDeleteConcurrencyTest : IntegrationTest() {

    @Autowired
    private lateinit var teamRepository: TeamRepository

    @Autowired
    private lateinit var teamMembershipRepository: TeamMembershipRepository

    @Autowired
    private lateinit var clusterRepository: ClusterRepository

    @Autowired
    private lateinit var chatSessionRepository: ChatSessionRepository

    @Autowired
    private lateinit var chatRunRepository: ChatRunRepository

    @Autowired
    private lateinit var actionPlanRepository: ActionPlanRepository

    @Autowired
    private lateinit var deleteTeamService: DeleteTeamService

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `요청을 마감하는 중에 팀을 지워도 교착하지 않는다`() {
        // given
        val team = savedTeam()
        val run = savedRun(savedSession(team))
        actionPlanRepository.save(ActionPlanFixture.plan(run.id))

        // when
        val deletion = deleteTeamWhile(
            team,
            hold = { chatRunRepository.lockByIdOrThrow(run.id) },
            thenDo = { actionPlanRepository.closeOpenPlans(run.id) },
        )

        // then
        deletion.isSuccess shouldBe true
        assertTeamGone(team)
    }

    @Test
    fun `계획을 만드는 중에 팀을 지우면 그 계획까지 지운다`() {
        // given
        val team = savedTeam()
        val run = savedRun(savedSession(team))

        // when
        val deletion = deleteTeamWhile(team, hold = {
            chatRunRepository.lockByIdOrThrow(run.id)
            actionPlanRepository.saveAndFlush(ActionPlanFixture.plan(run.id))
        })

        // then
        deletion.isSuccess shouldBe true
        assertTeamGone(team)
    }

    private fun deleteTeamWhile(team: Team, hold: () -> Unit, thenDo: () -> Unit = {}): Result<Unit> {
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
                    thenDo()
                }
            }
            holding.await(5, TimeUnit.SECONDS) shouldBe true

            val deletion = executor.submit<Result<Unit>> { runCatching { deleteTeamService(team.id) } }
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
        error("팀 삭제가 잠금을 기다리지 않았다")
    }

    private fun assertTeamGone(team: Team) {
        teamRepository.existsById(team.id) shouldBe false
        clusterRepository.count() shouldBe 0
        chatSessionRepository.count() shouldBe 0
        chatRunRepository.count() shouldBe 0
        actionPlanRepository.count() shouldBe 0
    }

    private fun savedTeam(): Team {
        val team = teamRepository.save(TeamFixture.team())
        teamMembershipRepository.save(TeamFixture.membership(team.id, owner.id))
        return team
    }

    private fun savedCluster(team: Team): Cluster =
        clusterRepository.save(ClusterFixture.cluster(registeredBy = owner.id, teamId = team.id))

    private fun savedSession(team: Team): ChatSession =
        chatSessionRepository.save(ChatFixture.session(owner.id, savedCluster(team).id, visibility = ChatVisibility.SHARED))

    private fun savedRun(session: ChatSession): ChatRun =
        chatRunRepository.save(ChatFixture.run(session.id))

    private val owner: User by lazy { userRepository.save(UserFixture.user()) }
}
