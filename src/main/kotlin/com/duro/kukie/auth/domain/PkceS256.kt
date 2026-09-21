package com.duro.kukie.auth.domain

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

/**
 * PKCE S256 — `challenge = base64url(sha256(verifier))`, 패딩 없음 (RFC 7636 §4.2). 구글·깃허브가 OAuth 에서 쓰는 것과 같은 식이다.
 *
 * 앱 넘겨주기(DURO-109)에 쓴다: 앱이 난수(verifier)를 만들어 해시(challenge)만 시스템 브라우저로 보내고, 코드를 바꿀 때 원본을 낸다.
 * `kukie://` 를 가로채 코드를 얻은 다른 프로그램은 verifier 가 없어 쿠키로 못 바꾼다.
 */
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
