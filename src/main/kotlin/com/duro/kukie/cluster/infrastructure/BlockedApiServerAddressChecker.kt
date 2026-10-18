package com.duro.kukie.cluster.infrastructure

import com.duro.kukie.cluster.application.port.out.ApiServerAddressChecker
import com.duro.kukie.cluster.exception.KubeconfigRejectedException
import com.duro.kukie.global.config.properties.ClusterProperties
import org.springframework.stereotype.Component
import java.net.InetAddress
import java.net.URI

@Component
class BlockedApiServerAddressChecker(
    private val hostResolver: HostResolver,
    private val clusterProperties: ClusterProperties,
) : ApiServerAddressChecker {

    override fun rejectionOf(apiServer: String): String? {
        if (apiServer.any { it.isWhitespace() }) return "클러스터 주소에 공백이 들어 있습니다: $apiServer"
        if (!apiServer.startsWith("https://")) return "클러스터 주소는 https 여야 합니다: $apiServer"
        val host = runCatching { URI(apiServer).host }.getOrNull()?.removeSurrounding("[", "]")
        if (host.isNullOrEmpty()) return "클러스터 주소에 호스트가 없습니다: $apiServer"
        if (clusterProperties.allowLocal) return null

        if (ipLiteral(host)?.let(BlockedAddresses::contains) == true) return "사설·로컬 주소는 등록할 수 없습니다: $host"
        val addresses = try {
            hostResolver.resolve(host)
        } catch (exception: KubeconfigRejectedException) {
            return exception.message
        }
        return addresses.firstOrNull(BlockedAddresses::contains)
            ?.let { "사설·로컬 주소로 풀리는 이름은 등록할 수 없습니다: $host → ${it.hostAddress}" }
    }

    private fun ipLiteral(host: String): InetAddress? {
        if (!IPV4_LITERAL.matches(host) && ':' !in host) return null
        // 없는 인터페이스 이름이 붙으면 JDK 가 못 읽어 검사가 새므로 범위 표기(%eth0)를 떼고 본다
        return runCatching { InetAddress.getByName(host.substringBefore('%')) }.getOrNull()
    }

    companion object {
        private val IPV4_LITERAL = Regex("(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(?:\\.(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}")
    }
}
