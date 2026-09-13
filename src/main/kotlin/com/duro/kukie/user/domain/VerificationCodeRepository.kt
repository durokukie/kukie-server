package com.duro.kukie.user.domain

import java.time.Duration

interface VerificationCodeRepository {
    fun save(email: String, code: String)

    fun findByEmail(email: String): String?

    fun deleteByEmail(email: String)

    companion object {
        /** 인증 코드의 유효 기간. 저장소의 TTL 과 메일 본문의 안내가 같은 값을 본다. */
        val EXPIRATION: Duration = Duration.ofMinutes(3)
    }
}
