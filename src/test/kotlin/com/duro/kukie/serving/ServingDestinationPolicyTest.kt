package com.duro.kukie.serving

import com.duro.kukie.cluster.infrastructure.HostResolver
import com.duro.kukie.global.config.properties.ServingRelayProperties
import com.duro.kukie.serving.exception.ServingAccessException
import com.duro.kukie.serving.infrastructure.ServingDestinationPolicy
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.net.InetAddress

class ServingDestinationPolicyTest {
    @Test
    fun `허용된 공개 HTTPS 호스트와 고정 경로만 등록한다`() {
        // given
        val policy = policy("93.184.216.34")
        // when then
        policy.validate("https://counter.example.com/v1/") shouldBe "https://counter.example.com/v1"
        for (url in listOf("http://counter.example.com", "https://user:pass@counter.example.com", "https://counter.example.com:8443", "https://counter.example.com/path", "https://counter.example.com/v1?url=x", "https://counter.example.com/#x", "https://counter.example.com.evil", "https://127.1", "https://counter.example.com/%2e%2e")) {
            shouldThrow<ServingAccessException> { policy.validate(url) }
        }
    }

    @Test
    fun `사설 메타데이터 CGNAT 멀티캐스트 및 IPv6 우회와 혼합 DNS를 차단한다`() {
        // given when then
        for (ip in listOf("127.0.0.1", "10.0.0.1", "169.254.169.254", "172.16.0.1", "192.168.1.1", "100.64.0.1", "100.127.255.254", "224.0.0.1", "::1", "fe80::1", "fd00::1", "::ffff:127.0.0.1", "2002:7f00:1::", "64:ff9b::7f00:1")) {
            shouldThrow<ServingAccessException> { policy(ip).validate("https://counter.example.com") }
        }
        val mixed = ServingDestinationPolicy(ServingRelayProperties(allowedHosts = setOf("counter.example.com")), HostResolver { listOf(InetAddress.getByName("93.184.216.34"), InetAddress.getByName("10.0.0.1")) })
        shouldThrow<ServingAccessException> { mixed.resolve("counter.example.com") }
    }

    @Test
    fun `빈 허용목록은 차단하고 연결 때 DNS가 바뀌어도 다시 검사한다`() {
        // given
        var address = "93.184.216.34"
        val resolver = HostResolver { listOf(InetAddress.getByName(address)) }
        val policy = ServingDestinationPolicy(ServingRelayProperties(allowedHosts = setOf("counter.example.com")), resolver)
        // when then
        policy.validate("https://counter.example.com")
        address = "169.254.169.254"
        shouldThrow<ServingAccessException> { policy.resolve("counter.example.com") }
        shouldThrow<ServingAccessException> { ServingDestinationPolicy(ServingRelayProperties(), resolver).validate("https://counter.example.com") }
    }

    private fun policy(ip: String) = ServingDestinationPolicy(ServingRelayProperties(allowedHosts = setOf("counter.example.com")), HostResolver { listOf(InetAddress.getByName(ip)) })
}
