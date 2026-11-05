package com.duro.kukie.serving.infrastructure

import com.duro.kukie.cluster.infrastructure.BlockedAddresses
import com.duro.kukie.cluster.infrastructure.HostResolver
import com.duro.kukie.global.config.properties.ServingRelayProperties
import com.duro.kukie.serving.exception.ServingAccessException
import com.duro.kukie.serving.exception.ServingErrorCode
import org.springframework.stereotype.Component
import java.net.InetAddress
import java.net.URI

@Component
class ServingDestinationPolicy(private val properties: ServingRelayProperties, private val resolver: HostResolver) {
    fun validate(baseUrl: String): String {
        val uri = runCatching { URI(baseUrl) }.getOrNull() ?: rejected()
        if (uri.scheme != "https" || uri.host == null || uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null ||
            uri.port !in listOf(-1, 443) || uri.rawPath !in listOf("", "/", "/v1", "/v1/")) rejected()
        resolve(uri.host)
        return "https://${uri.host.lowercase()}/v1"
    }

    fun resolve(host: String): Array<InetAddress> {
        if (host.lowercase() !in properties.allowedHosts.map(String::lowercase)) rejected()
        val addresses = try { resolver.resolve(host) } catch (_: Exception) { rejected() }
        if (addresses.isEmpty() || addresses.any(::blocked)) rejected()
        return addresses.toTypedArray()
    }

    private fun blocked(ip: InetAddress): Boolean {
        val bytes = ip.address
        val cgnat = bytes.size == 4 && bytes[0].toInt() and 255 == 100 && bytes[1].toInt() and 255 in 64..127
        return BlockedAddresses.contains(ip) || ip.isAnyLocalAddress || ip.isLoopbackAddress || ip.isSiteLocalAddress || ip.isLinkLocalAddress || ip.isMulticastAddress || cgnat
    }

    private fun rejected(): Nothing = throw ServingAccessException(ServingErrorCode.SERVING_DESTINATION_REJECTED)
}
