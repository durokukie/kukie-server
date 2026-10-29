package com.duro.kukie.gitops.domain

import java.time.Duration
import java.util.UUID

/** 설치 state 의 1회용 nonce 저장소. */
interface InstallStateRepository {
    fun save(nonce: String, teamId: UUID, ttl: Duration)

    /** 꺼내면서 원자적으로 지운다. 이미 썼거나 만료됐으면 null. */
    fun take(nonce: String): UUID?
}
