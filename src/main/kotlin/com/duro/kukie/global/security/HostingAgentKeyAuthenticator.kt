package com.duro.kukie.global.security

import java.util.UUID

/** 호스팅 에이전트 키(hk_…)의 주인. 없거나 끊었거나 만료된 키면 null. */
interface HostingAgentKeyAuthenticator {

    fun userIdOf(key: String): UUID?

    companion object {
        const val PREFIX = "hk_"
    }
}
