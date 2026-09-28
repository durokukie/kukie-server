package com.duro.kukie.auth.domain

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

/** PKCE S256 — `challenge = base64url(sha256(verifier))`, 패딩 없음 (RFC 7636 §4.2). */
object PkceS256 {

    fun challengeOf(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(StandardCharsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    /** 상수 시간 비교 — 앞글자부터 맞혀 가는 타이밍 공격을 막는다. */
    fun matches(verifier: String, challenge: String): Boolean =
        MessageDigest.isEqual(
            challengeOf(verifier).toByteArray(StandardCharsets.US_ASCII),
            challenge.toByteArray(StandardCharsets.US_ASCII),
        )
}
