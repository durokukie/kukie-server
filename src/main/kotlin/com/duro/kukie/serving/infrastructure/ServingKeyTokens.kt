package com.duro.kukie.serving.infrastructure

import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

@Component
class ServingKeyTokens {
    private val random = SecureRandom()
    private val format = Regex("kk_[A-Za-z0-9_-]{43}")

    fun generate(): String = "kk_" + Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
    fun isValidFormat(token: String): Boolean = format.matches(token)
    fun hash(token: String): String = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
