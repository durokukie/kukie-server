package com.duro.kukie.modelcatalog.domain

import java.time.Duration

interface ModelCandidateRefreshLock {

    /** 잡으면 토큰을 주고, 이미 잡혀 있으면 null. */
    fun tryAcquire(ttl: Duration): String?

    /** 토큰이 지금 잠금과 같을 때만 남은 시간을 ttl 로 바꾼다. */
    fun holdFor(token: String, ttl: Duration)

    /** 토큰이 지금 잠금과 같을 때만 푼다. 만료 뒤 남이 잡은 잠금을 풀지 않게 한다. */
    fun release(token: String)
}
