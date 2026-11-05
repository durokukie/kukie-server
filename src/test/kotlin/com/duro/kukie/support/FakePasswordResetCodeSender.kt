package com.duro.kukie.support

import com.duro.kukie.auth.application.port.out.PasswordResetCodeSender

class FakePasswordResetCodeSender : PasswordResetCodeSender, Resettable {

    private val sent = mutableListOf<Pair<String, String>>()

    override fun send(email: String, code: String) {
        sent += email to code
    }

    fun lastCodeFor(email: String): String =
        checkNotNull(sent.lastOrNull { it.first == email }?.second) { "$email 로 발송된 재설정 코드가 없습니다." }

    fun sentCount(email: String): Int = sent.count { it.first == email }

    override fun clear() = sent.clear()
}
