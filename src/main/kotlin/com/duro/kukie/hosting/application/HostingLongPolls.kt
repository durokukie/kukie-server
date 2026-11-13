package com.duro.kukie.hosting.application

import org.springframework.stereotype.Component
import java.util.concurrent.Semaphore

/**
 * 에이전트 요청을 붙잡아 기다리는 자리. 요청 스레드를 쓰므로 서버 전체에서 MAX 개까지만 기다리고, 자리가 없으면 기다리지 않고
 * 지금 상태를 곧바로 답한다(에이전트는 어차피 다시 부른다). 같은 pollToken 으로 수백 번 불러도 다른 API 의 스레드가 남는다.
 */
@Component
class HostingLongPolls {

    private val seats = Semaphore(MAX)

    /** 자리를 얻으면 wait(true), 못 얻으면 wait(false). */
    fun <T> hold(wait: (Boolean) -> T): T {
        if (!seats.tryAcquire()) return wait(false)
        try {
            return wait(true)
        } finally {
            seats.release()
        }
    }

    companion object {
        private const val MAX = 32
    }
}
