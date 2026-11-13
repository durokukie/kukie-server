package com.duro.kukie.hosting.application

import com.duro.kukie.hosting.exception.HostingTooManyRequestsException
import org.springframework.stereotype.Component
import java.util.concurrent.Semaphore

/**
 * 에이전트 요청을 붙잡아 기다리는 자리. 요청 스레드를 쓰므로 서버 전체에서 MAX 개까지만 둔다. 자리가 없으면 429 로 답해 에이전트가
 * 잠시 쉬었다 다시 부르게 한다(곧바로 답하면 쉬지 않고 다시 부른다).
 */
@Component
class HostingLongPolls {

    private val seats = Semaphore(MAX)

    fun <T> hold(block: () -> T): T {
        if (!seats.tryAcquire()) throw HostingTooManyRequestsException()
        try {
            return block()
        } finally {
            seats.release()
        }
    }

    companion object {
        private const val MAX = 32
    }
}
