package com.duro.kukie.user.domain

interface VerificationCodeRepository {
    fun save(email: String, code: String)

    fun findByEmail(email: String): String?

    fun deleteByEmail(email: String)
}
