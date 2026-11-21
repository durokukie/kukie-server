package com.duro.kukie.activity.domain

import java.time.Instant
import java.util.UUID

interface ActiveUserRepository {
    fun record(userId: UUID, at: Instant)

    /** at 까지 window 안에 한 번이라도 기록된 사용자 수(근사값). */
    fun count(window: ActiveWindow, at: Instant): Long
}
