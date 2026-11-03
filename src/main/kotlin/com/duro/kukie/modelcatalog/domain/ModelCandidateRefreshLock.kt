package com.duro.kukie.modelcatalog.domain

import java.time.Duration

interface ModelCandidateRefreshLock {

    /** 잡으면 풀 때 쓸 토큰을 주고, 이미 잡혀 있으면 null. 성공한 갱신은 풀지 않아 시간이 지나야 풀린다. */
    fun tryAcquire(ttl: Duration): String?

    /** 토큰이 지금 잠금과 같을 때만 푼다. 만료 뒤 남이 잡은 잠금을 풀지 않게 한다. */
    fun release(token: String)
}
