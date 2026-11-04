package com.duro.kukie.cluster.domain

import java.time.Duration
import java.util.UUID

/** 사용자당 모델 대화 1건. 서버가 여러 대여도 같게 동작하고, 서버가 죽으면 ttl 뒤에 풀린다. */
interface ModelChatLock {

    /** 잡으면 놓을 때 쓸 토큰, 이미 누가 잡고 있으면 null. */
    fun tryAcquire(userId: UUID, ttl: Duration): String?

    fun holdFor(userId: UUID, token: String, ttl: Duration)

    fun release(userId: UUID, token: String)
}
