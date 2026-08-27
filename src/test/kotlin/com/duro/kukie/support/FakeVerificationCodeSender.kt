package com.duro.kukie.support

import com.duro.kukie.user.application.port.VerificationCodeSender
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class FakeVerificationCodeSender : VerificationCodeSender {

    private val sentCodes = mutableMapOf<String, String>()

    override fun send(email: String, code: String) {
        sentCodes[email] = code
    }

    fun lastCodeFor(email: String): String =
        checkNotNull(sentCodes[email]) { "$email 로 발송된 인증 코드가 없습니다." }

    fun clear() {
        sentCodes.clear()
    }
}

@TestConfiguration(proxyBeanMethods = false)
class FakeVerificationCodeSenderConfig {

    @Bean
    @Primary
    fun fakeVerificationCodeSender(): FakeVerificationCodeSender = FakeVerificationCodeSender()
}
