package com.duro.kukie.hosting.domain

import java.time.Duration
import java.time.Instant

/**
 * 시작하지 못한(start_failed) 배포 하나에 대해 서버가 한 일. AI 가 시작 명령을 고르는 중(analyzing), 그 명령으로 다시 배포를 보냄(sent),
 * 다시 배포하지 않음(skipped). reason 은 AI 가 그 명령을 고른 까닭이나 다시 배포하지 않은 까닭이다.
 */
data class HostingStartRetry(
    val status: String,
    val at: Instant,
    val start: String? = null,
    val reason: String? = null,
    val commandId: String? = null,
) {
    /** 고르다 서버가 멈췄으면 고르는 중으로 남는다. 오래됐으면 다시 배포하지 않은 것으로 본다. */
    fun settled(now: Instant): HostingStartRetry =
        if (status == ANALYZING && Duration.between(at, now) > ANALYZING_LIMIT) copy(status = SKIPPED, reason = NO_ANSWER) else this

    companion object {
        const val ANALYZING = "analyzing"
        const val SENT = "sent"
        const val SKIPPED = "skipped"
        const val NO_ANSWER = "AI 가 시간 안에 답하지 않았어요."
        private val ANALYZING_LIMIT: Duration = Duration.ofMinutes(3)
    }
}

interface HostingStartRetryRepository {

    /** 배포의 재시도 자리를 잡는다. 이미 있으면 false. */
    fun claim(key: String, retry: HostingStartRetry): Boolean

    fun find(key: String): HostingStartRetry?

    fun save(key: String, retry: HostingStartRetry)
}
