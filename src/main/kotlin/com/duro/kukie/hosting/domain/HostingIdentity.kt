package com.duro.kukie.hosting.domain

import com.duro.kukie.user.domain.GitHubAccount
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID

/**
 * 호스팅에서 팀과 앱의 주인. GitHub 를 연결한 회원은 그 GitHub 계정이고, 연결하지 않은 회원은 회원 id 로 만든 Kukie 계정이다.
 * 배포기는 둘을 가리지 않는다: id 는 팀의 주인 표시, login 의 소문자가 팀 이름이다. Kukie 계정은 레포를 잇지 못하고 업로드로만 배포한다.
 */
data class HostingIdentity(val id: Long, val login: String) {

    val team: String
        get() = login.lowercase()

    val gitHub: Boolean
        get() = id < KUKIE_ID_BASE

    companion object {
        // GitHub 숫자 id 와 겹치지 않고, 화면이 숫자로 읽어도 정확한(2^53 미만) 범위다
        private const val KUKIE_ID_BASE = 1_000_000_000_000_000L
        private const val KUKIE_BITS = 48
        private const val KUKIE_LOGIN_PREFIX = "u-"

        fun of(userId: UUID, gitHub: GitHubAccount?): HostingIdentity =
            gitHub?.let { HostingIdentity(it.id, it.login) } ?: kukie(userId)

        fun kukie(userId: UUID): HostingIdentity {
            val digest = MessageDigest.getInstance("SHA-256").digest(userId.toString().toByteArray())
            val bits = ByteBuffer.wrap(digest).long ushr (Long.SIZE_BITS - KUKIE_BITS)
            return HostingIdentity(KUKIE_ID_BASE + bits, KUKIE_LOGIN_PREFIX + bits.toString(36).padStart(10, '0'))
        }
    }
}
