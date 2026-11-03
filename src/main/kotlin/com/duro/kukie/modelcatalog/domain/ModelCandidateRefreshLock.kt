package com.duro.kukie.modelcatalog.domain

import java.time.Duration

interface ModelCandidateRefreshLock {

    /** 이미 잡혀 있으면 false. 성공한 갱신은 풀지 않아 시간이 지나야 풀린다. */
    fun tryAcquire(ttl: Duration): Boolean

    fun release()
}
