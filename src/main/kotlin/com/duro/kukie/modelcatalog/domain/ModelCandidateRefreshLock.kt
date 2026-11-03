package com.duro.kukie.modelcatalog.domain

import java.time.Duration

interface ModelCandidateRefreshLock {

    /** 이미 잡혀 있으면 false. 풀지 않고 시간이 지나면 풀린다. */
    fun tryAcquire(ttl: Duration): Boolean
}
