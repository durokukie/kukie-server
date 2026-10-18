package com.duro.kukie.cluster.infrastructure

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test
import software.amazon.awssdk.identity.spi.AwsSessionCredentialsIdentity
import java.net.URI
import java.net.URLDecoder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64

class EksTokenSignerTest {

    private val clock = Clock.fixed(Instant.parse("2026-10-19T09:00:00Z"), ZoneOffset.UTC)
    private val credentials = AwsSessionCredentialsIdentity.create("AKIDEXAMPLE", "secret-key", "session-token")
    private val signer = EksTokenSigner(clock)

    @Test
    fun `x-k8s-aws-id 를 서명한 STS GetCallerIdentity presigned URL 로 토큰을 만든다`() {
        // when
        val token = signer.sign(credentials, "ap-northeast-2", "demo")

        // then
        token shouldStartWith "k8s-aws-v1."
        token.contains('=') shouldBe false
        val url = URI(String(Base64.getUrlDecoder().decode(token.removePrefix("k8s-aws-v1.")), Charsets.UTF_8))
        url.scheme shouldBe "https"
        url.host shouldBe "sts.ap-northeast-2.amazonaws.com"
        val query = query(url)
        query["Action"] shouldBe "GetCallerIdentity"
        query["Version"] shouldBe "2011-06-15"
        query["X-Amz-Algorithm"] shouldBe "AWS4-HMAC-SHA256"
        query["X-Amz-Expires"] shouldBe "60"
        query["X-Amz-Date"] shouldBe "20261019T090000Z"
        query["X-Amz-Credential"] shouldBe "AKIDEXAMPLE/20261019/ap-northeast-2/sts/aws4_request"
        query["X-Amz-Security-Token"] shouldBe "session-token"
        query.getValue("X-Amz-SignedHeaders").split(';').toSet() shouldBe setOf("host", "x-k8s-aws-id")
        query.getValue("X-Amz-Signature").length shouldBe 64
    }

    @Test
    fun `같은 입력과 시각이면 같은 토큰을, 클러스터가 다르면 다른 서명을 만든다`() {
        // when
        val first = signer.sign(credentials, "ap-northeast-2", "demo")
        val again = signer.sign(credentials, "ap-northeast-2", "demo")
        val other = signer.sign(credentials, "ap-northeast-2", "other")

        // then
        first shouldBe again
        (first == other) shouldBe false
    }

    private fun query(url: URI): Map<String, String> =
        url.rawQuery.split('&').associate { pair ->
            val (key, value) = pair.split('=', limit = 2)
            URLDecoder.decode(key, Charsets.UTF_8) to URLDecoder.decode(value, Charsets.UTF_8)
        }
}
