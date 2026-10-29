package com.duro.kukie.gitops.domain

import java.time.Instant
import java.util.UUID

/** 설치를 시작한 팀과 사람. nonce 는 한 번만 쓸 수 있다. */
data class InstallState(
    val teamId: UUID,
    val userId: UUID,
    val nonce: String,
    val expiresAt: Instant,
)
