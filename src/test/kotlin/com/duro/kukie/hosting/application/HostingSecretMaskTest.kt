package com.duro.kukie.hosting.application

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class HostingSecretMaskTest {

    @Test
    fun `키·토큰처럼 보이는 값과 주소의 비밀번호를 가린다`() {
        // given
        val log = """
            OPENAI_API_KEY=sk-abcdefghijklmnopqrstuvwx
            password: "hunter2"
            Authorization: Bearer abc.def
            Authorization: Basic dXNlcjpwYXNzd29yZA==
            proxy-authorization=Zm9vOmJhcg==
            curl -H "Authorization: Basic Zm9vOmJhcg=="
            using basic dXNlcjpwYXNzd29yZA== for the registry
            postgres://kukie:s3cret@db:5432/app
            token ghp_abcdefghijklmnopqrstuvwxyz0123
        """.trimIndent()

        // when
        val masked = HostingSecretMask.mask(log)

        // then
        masked shouldBe """
            OPENAI_API_KEY=***
            password: ***
            Authorization: Bearer ***
            Authorization: Basic ***
            proxy-authorization=***
            curl -H "Authorization: Basic ***"
            using basic *** for the registry
            postgres://***:***@db:5432/app
            token ***
        """.trimIndent()
    }

    @Test
    fun `원인을 알려 주는 줄은 가리지 않는다`() {
        // given
        val log = "KeyError: 'DATABASE_URL'\nauthor: kukie\nbasic authentication required\nModuleNotFoundError: No module named 'flask'"

        // when & then
        HostingSecretMask.mask(log) shouldBe log
    }
}
