package com.duro.kukie.user.domain

import com.duro.kukie.global.domain.Email
import java.time.Duration

interface VerificationCodeRepository {
    fun save(email: Email, code: String)

    fun findByEmail(email: Email): String?

    fun deleteByEmail(email: Email)

    companion object {
        val EXPIRATION: Duration = Duration.ofMinutes(3)
    }
}
