package com.duro.kukie.auth.domain

import java.util.UUID

/**
 * 리프레시 토큰을 로그인(세션)마다 하나씩 둔다. 한 사용자가 여러 창·기기에서 로그인해도 서로를 끊지 않는다.
 */
interface RefreshTokenRepository {

    /** 새 세션을 연다. 사용자 세션이 상한을 넘으면 가장 오래 안 쓴 세션부터 지운다. */
    fun create(userId: UUID, sessionId: String, refreshToken: String)

    /** 그 세션의 토큰이 [presented] 이면 [next] 로 바꾼다. 결과는 [RefreshTokenRotation] 참고. */
    fun rotate(userId: UUID, sessionId: String, presented: String, next: String): RefreshTokenRotation

    /** 그 세션만 지운다. 다른 세션은 그대로 둔다. */
    fun delete(userId: UUID, sessionId: String)

    /** 그 사용자의 세션을 모두 지운다(세션 도입 전 키 포함). 탈퇴할 때 쓴다. */
    fun deleteAll(userId: UUID)

    /**
     * 세션 도입 전 키(사용자당 하나)의 토큰이 [presented] 이면 true 를 주고 그 키를 겹침 시간 뒤에 사라지게 한다.
     * 옛 토큰을 새 세션으로 옮길 때 쓴다.
     */
    fun takeLegacy(userId: UUID, presented: String): Boolean

    /** 세션 도입 전 키를 지운다. 세션 id 없는 옛 액세스 토큰으로 로그아웃할 때 쓴다. */
    fun deleteLegacy(userId: UUID)
}

sealed interface RefreshTokenRotation {
    /** 지금 토큰이 맞아 새 토큰으로 바꿨다. */
    data object Rotated : RefreshTokenRotation

    /** 방금 바뀐 직전 토큰이 겹침 시간 안에 다시 왔다(같은 브라우저 다른 탭의 동시 재발급). 지금 토큰을 그대로 돌려준다. */
    data class RecentlyRotated(val currentToken: String) : RefreshTokenRotation

    /** 이미 돌려 쓴 옛 토큰이 다시 왔다. 그 세션을 지웠다. */
    data object Reused : RefreshTokenRotation

    /** 세션이 없다(로그아웃·만료·상한으로 지워짐). */
    data object Missing : RefreshTokenRotation
}
