package com.duro.kukie.hosting.application.port.out

import java.time.Duration

interface HostingAgentRateLimit {

    /** key 의 요청이 window 안에 limit 번을 넘으면 false. */
    fun tryAcquire(key: String, limit: Int, window: Duration): Boolean
}
