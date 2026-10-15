package com.duro.kukie.actionplan

import com.duro.kukie.actionplan.ActionPlanFixture.offered
import com.duro.kukie.actionplan.application.DecidePlanService
import com.duro.kukie.actionplan.application.port.`in`.DecidePlanCommand
import com.duro.kukie.actionplan.domain.ActionPlan
import com.duro.kukie.actionplan.domain.ActionPlanRepository
import com.duro.kukie.actionplan.exception.PlanStateConflictException
import com.duro.kukie.chat.ChatFixture
import com.duro.kukie.chat.domain.ChatRunRepository
import com.duro.kukie.chat.domain.ChatSessionRepository
import com.duro.kukie.chat.domain.ChatVisibility
import com.duro.kukie.cluster.ClusterFixture
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.team.domain.TeamRole
import com.duro.kukie.user.UserFixture
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ActionPlanConcurrencyTest : IntegrationTest() {

    @Autowired
    private lateinit var clusterRepository: ClusterRepository

    @Autowired
    private lateinit var chatSessionRepository: ChatSessionRepository

    @Autowired
    private lateinit var chatRunRepository: ChatRunRepository

    @Autowired
    private lateinit var actionPlanRepository: ActionPlanRepository

    @Autowired
    private lateinit var decidePlanService: DecidePlanService

    @Test
    fun `동시에 승인해도 결정은 한 번만 남는다`() {
        // given
        val plan = savedPlan { it.offered() }

        // when
        val results = concurrently(THREADS) { index ->
            val decider = UUID(0, index.toLong())
            decidePlanService(DecidePlanCommand(plan.id, approved = true, userId = decider, call = ActionPlanFixture.call()))
        }

        // then
        val winner = results.single { it.isSuccess }.getOrThrow()
        results.count { it.exceptionOrNull() is PlanStateConflictException } shouldBe THREADS - 1
        actionPlanRepository.findByIdOrNull(plan.id)!!.decision shouldBe winner.decision
    }

    private fun <T> concurrently(threads: Int, task: (Int) -> T): List<Result<T>> {
        val executor = Executors.newFixedThreadPool(threads)
        val ready = CountDownLatch(threads)
        val go = CountDownLatch(1)
        try {
            val futures = (0 until threads).map { index ->
                executor.submit<Result<T>> {
                    ready.countDown()
                    go.await()
                    runCatching { task(index) }
                }
            }
            ready.await(5, TimeUnit.SECONDS)
            go.countDown()
            return futures.map { it.get(10, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun savedPlan(prepare: (ActionPlan) -> ActionPlan): ActionPlan {
        val owner = userRepository.save(UserFixture.user())
        val cluster = clusterRepository.save(ClusterFixture.cluster(registeredBy = owner.id, teamId = savedTeam(owner.id to TeamRole.ADMIN)))
        val session = chatSessionRepository.save(ChatFixture.session(owner.id, cluster.id, visibility = ChatVisibility.SHARED))
        val run = chatRunRepository.save(ChatFixture.run(session.id))
        return actionPlanRepository.save(prepare(ActionPlanFixture.plan(run.id)))
    }

    companion object {
        private const val THREADS = 8
    }
}
