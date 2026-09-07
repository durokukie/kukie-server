package com.duro.kukie.support

import com.duro.kukie.user.application.port.out.VerificationCodeSender

class FakeVerificationCodeSender : VerificationCodeSender, Resettable {

    private val sentCodes = mutableMapOf<String, String>()

    override fun send(email: String, code: String) {
        sentCodes[email] = code
    }

    fun lastCodeFor(email: String): String =
        checkNotNull(sentCodes[email]) { "$email 로 발송된 인증 코드가 없습니다." }

    override fun clear() = sentCodes.clear()
}
