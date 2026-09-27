package com.duro.kukie.global.config.properties

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration

class OAuthPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(OAuthProperties::class)
    class Config

    private val filled = listOf(
        "oauth.github.client-id=gh", "oauth.github.client-secret=gh",
        "oauth.github.web-client-id=gh-web", "oauth.github.web-client-secret=gh-web",
        "oauth.google.client-id=g", "oauth.google.client-secret=g",
        "oauth.google.web-client-id=g-web", "oauth.google.web-client-secret=g-web",
    )

    private fun runner(properties: List<String>) =
        ApplicationContextRunner().withUserConfiguration(Config::class.java).withPropertyValues(*properties.toTypedArray())

    @Test
    fun `웹 쌍이 다 있으면 켜진다`() {
        runner(filled).run { it.startupFailure.shouldBeNull() }
    }

    @Test
    fun `웹 쌍이 빈 문자열이면 켜지지 않는다`() {
        // 컴포즈는 .env 에 없는 값을 ${VAR:-} 로 빈 문자열로 넘긴다
        runner(filled + "oauth.github.web-client-secret=").run {
            it.startupFailure.shouldNotBeNull().stackTraceToString() shouldContain "webClientSecret"
        }
    }

    @Test
    fun `웹 쌍이 아예 없으면 켜지지 않는다`() {
        runner(filled.filterNot { it.startsWith("oauth.google.web-client-id") }).run {
            it.startupFailure.shouldNotBeNull()
        }
    }
}
