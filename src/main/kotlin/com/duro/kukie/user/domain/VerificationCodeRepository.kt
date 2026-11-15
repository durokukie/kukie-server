package com.duro.kukie.user.domain

import com.duro.kukie.global.domain.Email
import java.time.Duration

interface VerificationCodeRepository {
    fun save(type: VerificationType, email: Email, code: String)

    fun findByEmail(type: VerificationType, email: Email): String?

    fun deleteByEmail(type: VerificationType, email: Email)

    companion object {
        val EXPIRATION: Duration = Duration.ofMinutes(3)
    }
}
