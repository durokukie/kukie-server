package com.duro.kukie.support

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration(proxyBeanMethods = false)
class FakeBeansConfig {

    @Bean
    @Primary
    fun fakeVerificationCodeSender(): FakeVerificationCodeSender = FakeVerificationCodeSender()

    @Bean
    @Primary
    fun fakeOAuthClient(): FakeOAuthClient = FakeOAuthClient()
}
