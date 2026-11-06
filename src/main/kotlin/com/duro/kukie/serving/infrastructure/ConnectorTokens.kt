package com.duro.kukie.serving.infrastructure

import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.HexFormat

@Component
class ConnectorTokens {
    private val random = SecureRandom()
    fun generate(): String = HexFormat.of().formatHex(ByteArray(32).also { random.nextBytes(it) })
    fun hash(token: String): String = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.toByteArray()))
}
