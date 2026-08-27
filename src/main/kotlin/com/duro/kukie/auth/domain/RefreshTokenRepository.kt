package com.duro.kukie.auth.domain

import java.util.UUID

interface RefreshTokenRepository {
    fun save(userId: UUID, refreshToken: String)

    fun findByUserId(userId: UUID): String?

    fun deleteByUserId(userId: UUID)
}
