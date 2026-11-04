package com.duro.kukie.cluster.application

import com.duro.kukie.cluster.application.port.out.AwsTarget
import com.duro.kukie.cluster.application.port.out.EksAccessGateway
import com.duro.kukie.cluster.application.port.out.EksClusterEndpoint
import com.duro.kukie.cluster.domain.ModelChatLock
import com.duro.kukie.cluster.exception.GpuClusterApiFailedException
import com.duro.kukie.cluster.exception.ModelChatBusyException
import com.duro.kukie.cluster.exception.ModelChatTimeoutException
import com.duro.kukie.cluster.presentation.dto.response.ModelChatDelta
import com.duro.kukie.cluster.presentation.dto.response.ModelChatDone
import com.duro.kukie.cluster.presentation.dto.response.ModelChatUsage
import com.duro.kukie.global.config.properties.ClusterGpuProperties
import com.duro.kukie.global.exception.BusinessException
import com.duro.kukie.global.exception.ErrorCode
import com.duro.kukie.global.exception.ErrorResponse
import com.duro.kukie.global.util.logger
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * 모델의 응답을 브라우저로 그대로 옮긴다. 응답은 길게 이어지므로 톰캣 스레드를 쥐지 않고 대화마다 가상 스레드 둘을 쓴다.
 * 내용은 흘려보내기만 한다. 로그에는 토큰 수와 오류 코드만 남기고, 예외는 클래스 이름 말고 남기지 않는다.
 */
@Component
class ModelChatRelay(
    private val eksAccessGateway: EksAccessGateway,
    private val modelChatLock: ModelChatLock,
    private val gpuAuditLog: GpuAuditLog,
    private val properties: ClusterGpuProperties,
) {

    private val log = logger()
    private val slots = Semaphore(properties.chatMaxStreams)
    private val threads = Thread.ofVirtual().name("model-chat-", 0).factory()

    /** 사용자당 1건을 넘거나 이 서버의 대화 수 상한을 넘으면 ModelChatBusyException. */
    fun start(target: ModelChatTarget, body: ByteArray): SseEmitter {
        val lease = modelChatLock.tryAcquire(target.userId, LOCK_TTL) ?: throw ModelChatBusyException()
        if (!slots.tryAcquire()) {
            modelChatLock.release(target.userId, lease)
            throw ModelChatBusyException()
        }
        val emitter = SseEmitter((properties.chatTimeout + EMITTER_MARGIN).toMillis())
        val run = Run(target, body, lease, emitter)
        try {
            run.start()
        } catch (failure: Throwable) {
            run.release()
            throw failure
        }
        return emitter
    }

    private inner class Run(
        private val target: ModelChatTarget,
        private val body: ByteArray,
        private val lease: String,
        private val emitter: SseEmitter,
    ) {
        private val pumpThread = threads.newThread(::pump)
        private val heartThread = threads.newThread(::beat)
        private val finished = CountDownLatch(1)
        private val startedAt = System.nanoTime()
        private val deadline = startedAt + properties.chatTimeout.toNanos()

        @Volatile
        private var clientGone = false

        @Volatile
        private var timedOut = false

        private var firstTokenAt: Long? = null
        private var lastTokenAt: Long? = null
        private var finishReason: String? = null
        private var usage: ModelChatUsage? = null

        fun start() {
            emitter.onError { stopForGoneClient() }
            emitter.onTimeout {
                stopForGoneClient()
                emitter.complete()
            }
            pumpThread.start()
        }

        private fun stopForGoneClient() {
            clientGone = true
            pumpThread.interrupt()
        }

        private fun pump() {
            var outcome = "done"
            try {
                heartThread.start()
                // CloudFront 는 첫 바이트도 60초 안에 받아야 한다. 받는 쪽도 연결이 열린 것을 바로 안다
                sendKeepAlive()
                relay()
            } catch (_: ClientGoneException) {
                outcome = "aborted"
            } catch (exception: BusinessException) {
                outcome = endWithError(exception.errorCode, exception.errorCode.code)
            } catch (exception: RuntimeException) {
                log.warn("모델 대화를 옮기지 못했습니다: cluster={} reason={}", target.clusterId, exception.javaClass.simpleName)
                outcome = endWithError(GpuClusterApiFailedException().errorCode, exception.javaClass.simpleName)
            } finally {
                finished.countDown()
                // 이 스레드는 끊는 인터럽트를 받으므로 Redis 호출을 맡기지 않는다. 받으면 Redis 호출이 실패해 사용자가 잠금 만료까지 막힌다
                threads.newThread(::release).start()
                runCatching { emitter.complete() }
                gpuAuditLog.finished(
                    target.userId,
                    target.clusterId,
                    "chat namespace=${target.namespace} name=${target.name} model=${target.model} outcome=$outcome " +
                        "promptTokens=${usage?.promptTokens} completionTokens=${usage?.completionTokens}",
                )
            }
        }

        private fun relay() {
            eksAccessGateway.openModelChat(
                target.awsTarget,
                target.region,
                target.clusterName,
                target.endpoint,
                target.namespace,
                target.name,
                body,
                properties.chatTimeout,
            ).use { stream ->
                var ended = false
                while (!ended) {
                    val line = stream.readLine() ?: break
                    when (val parsed = VllmChatLine.parse(line)) {
                        null -> Unit
                        VllmChatLine.End -> ended = true
                        is VllmChatLine.Chunk -> forward(parsed)
                    }
                }
                // 프록시를 지나다 잘리면 오류 없이 끝날 수 있다
                if (!ended && finishReason == null) throw GpuClusterApiFailedException()
            }
            send(
                SseEmitter.event().name(EVENT_DONE).data(
                    ModelChatDone(usage, ttftMillis(), tokensPerSecond(), finishReason),
                    MediaType.APPLICATION_JSON,
                ),
            )
        }

        private fun forward(chunk: VllmChatLine.Chunk) {
            chunk.finishReason?.let { finishReason = it }
            chunk.usage?.let { usage = it }
            if (chunk.content == null && chunk.reasoning == null) return

            val now = System.nanoTime()
            if (firstTokenAt == null) firstTokenAt = now
            lastTokenAt = now
            send(
                SseEmitter.event().name(EVENT_DELTA).data(
                    ModelChatDelta(chunk.content, chunk.reasoning),
                    MediaType.APPLICATION_JSON,
                ),
            )
        }

        private fun ttftMillis(): Long? = firstTokenAt?.let { TimeUnit.NANOSECONDS.toMillis(it - startedAt) }

        private fun tokensPerSecond(): Double? {
            val tokens = usage?.completionTokens ?: return null
            val first = firstTokenAt ?: return null
            val seconds = ((lastTokenAt ?: first) - first) / NANOS_PER_SECOND
            return if (seconds > 0) tokens / seconds else null
        }

        /** 끊는 인터럽트가 상위 호출의 실패로 나타나므로 왜 멈췄는지를 먼저 본다. */
        private fun endWithError(errorCode: ErrorCode, reason: String): String {
            Thread.interrupted()
            if (clientGone) return "aborted"
            val (code, outcome) = when {
                timedOut -> ModelChatTimeoutException().errorCode to "timeout"
                else -> errorCode to "failed reason=$reason"
            }
            runCatching {
                send(SseEmitter.event().name(EVENT_ERROR).data(ErrorResponse(code.code, code.message), MediaType.APPLICATION_JSON))
            }
            return outcome
        }

        /** 말이 없는 동안에도 바이트를 보내 CloudFront 의 60초 제한을 넘기고, 쓰기 실패로 브라우저가 끊은 것을 안다. */
        private fun beat() {
            while (!finished.await(properties.chatHeartbeat.toMillis(), TimeUnit.MILLISECONDS)) {
                if (System.nanoTime() - deadline >= 0) {
                    timedOut = true
                    pumpThread.interrupt()
                    return
                }
                try {
                    sendKeepAlive()
                } catch (_: ClientGoneException) {
                    pumpThread.interrupt()
                    return
                }
                try {
                    modelChatLock.holdFor(target.userId, lease, LOCK_TTL)
                } catch (exception: RuntimeException) {
                    log.warn("모델 대화 잠금을 늘리지 못했습니다: reason={}", exception.javaClass.simpleName)
                }
            }
        }

        private fun sendKeepAlive() = send(SseEmitter.event().comment(KEEP_ALIVE))

        private fun send(event: SseEmitter.SseEventBuilder) {
            try {
                emitter.send(event)
            } catch (_: IOException) {
                clientGone = true
                throw ClientGoneException()
            } catch (_: IllegalStateException) {
                clientGone = true
                throw ClientGoneException()
            }
        }

        fun release() {
            slots.release()
            try {
                modelChatLock.release(target.userId, lease)
            } catch (exception: RuntimeException) {
                log.warn("모델 대화 잠금을 풀지 못했습니다: reason={}", exception.javaClass.simpleName)
            }
        }
    }

    private class ClientGoneException : RuntimeException(null, null, false, false)

    private companion object {
        const val EVENT_DELTA = "delta"
        const val EVENT_DONE = "done"
        const val EVENT_ERROR = "error"
        const val KEEP_ALIVE = "keep-alive"
        const val NANOS_PER_SECOND = 1_000_000_000.0

        // 서버가 죽었을 때 사용자가 이만큼 기다린다. 멈춘 브라우저에 쓰느라 심장 박동이 늦어져도 풀리지 않을 만큼은 둔다
        val LOCK_TTL: Duration = Duration.ofMinutes(2)

        // 시간 상한은 심장 박동이 지킨다. 이것은 그 스레드가 막혔을 때의 마지막 안전장치다
        val EMITTER_MARGIN: Duration = Duration.ofMinutes(2)
    }
}

data class ModelChatTarget(
    val userId: UUID,
    val clusterId: UUID,
    val awsTarget: AwsTarget,
    val region: String,
    val clusterName: String,
    val endpoint: EksClusterEndpoint,
    val namespace: String,
    val name: String,
    val model: String,
)
