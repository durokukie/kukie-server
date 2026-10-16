package com.duro.kukie.chat

import com.duro.kukie.chat.application.StartRunService
import com.duro.kukie.chat.application.port.`in`.StartRunCommand
import com.duro.kukie.chat.domain.ChatRunRepository
import com.duro.kukie.chat.domain.ChatSession
import com.duro.kukie.chat.domain.ChatSessionRepository
import com.duro.kukie.chat.domain.RunKind
import com.duro.kukie.chat.exception.ActiveRunExistsException
import com.duro.kukie.chat.presentation.dto.response.StartRunResponse
import com.duro.kukie.cluster.ClusterFixture
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.team.domain.TeamRole
import com.duro.kukie.user.UserFixture
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class RunStartConcurrencyTest : IntegrationTest() {

    @Autowired
    private lateinit var clusterRepository: ClusterRepository

    @Autowired
    private lateinit var chatSessionRepository: ChatSessionRepository

    @Autowired
    private lateinit var chatRunRepository: ChatRunRepository

    @Autowired
    private lateinit var startRunService: StartRunService

    @Test
    fun `요청을 동시에 시작해도 하나만 만들어진다`() {
        // given
        val session = savedSession()

        // when
        val results = concurrently(THREADS) { index -> startRunService(command(session, "req-$index")) }

        // then
        results.count { it.getOrNull()?.created == true } shouldBe 1
        results.count { it.exceptionOrNull() is ActiveRunExistsException } shouldBe THREADS - 1
        chatRunRepository.count() shouldBe 1
    }

    private fun concurrently(threads: Int, task: (Int) -> StartRunResponse): List<Result<StartRunResponse>> {
        val executor = Executors.newFixedThreadPool(threads)
        val ready = CountDownLatch(threads)
        val go = CountDownLatch(1)
        try {
            val futures = (0 until threads).map { index ->
                executor.submit<Result<StartRunResponse>> {
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

    private fun savedSession(): ChatSession {
        val owner = userRepository.save(UserFixture.user())
        val cluster = clusterRepository.save(ClusterFixture.cluster(registeredBy = owner.id, teamId = savedTeam(owner.id to TeamRole.ADMIN)))
        return chatSessionRepository.save(ChatFixture.session(owner.id, cluster.id))
    }

    private fun command(session: ChatSession, requestId: String) = StartRunCommand(
        conversationId = session.id,
        userId = session.userId,
        requestId = requestId,
        kind = RunKind.CHAT,
        mode = "학습",
        inputText = "파드 보여줘",
    )

    companion object {
        private const val THREADS = 8
    }
}
