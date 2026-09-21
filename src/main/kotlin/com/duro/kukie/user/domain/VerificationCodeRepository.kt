package com.duro.kukie.user.domain

import java.time.Duration

interface VerificationCodeRepository {
    fun save(email: String, code: String)

    fun findByEmail(email: String): String?

    fun deleteByEmail(email: String)

    companion object {
        val EXPIRATION: Duration = Duration.ofMinutes(3)
    }
}
