package com.duro.kukie.serving.infrastructure

import com.duro.kukie.global.config.properties.ServingRelayProperties
import com.duro.kukie.serving.application.port.out.CounterExchange
import com.duro.kukie.serving.application.port.out.CounterResponse
import com.duro.kukie.serving.application.port.out.CounterTarget
import com.duro.kukie.serving.application.port.out.CounterTransport
import org.apache.hc.client5.http.DnsResolver
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase
import org.apache.hc.client5.http.config.ConnectionConfig
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder
import org.apache.hc.core5.http.ContentType
import org.apache.hc.core5.http.io.entity.ByteArrayEntity
import org.apache.hc.core5.util.Timeout
import org.springframework.beans.factory.DisposableBean
import org.springframework.stereotype.Component
import java.io.InputStream
import java.net.URI

@Component
class HttpsCounterTransport internal constructor(private val client: org.apache.hc.client5.http.impl.classic.CloseableHttpClient) : CounterTransport, DisposableBean {
    @org.springframework.beans.factory.annotation.Autowired
    constructor(policy: ServingDestinationPolicy, properties: ServingRelayProperties) : this(counterHttpClient(policy, properties))

    override fun create(target: CounterTarget, path: String, body: ByteArray?): CounterExchange {
        check(path == "/models" || path == "/chat/completions")
        val request = HttpUriRequestBase(if (body == null) "GET" else "POST", URI(target.baseUrl + path))
        request.setHeader("Authorization", "Bearer ${target.serviceToken}")
        request.setHeader("Accept", "application/json, text/event-stream")
        request.setHeader("Accept-Encoding", "identity")
        body?.let { request.entity = ByteArrayEntity(it, ContentType.APPLICATION_JSON) }
        return object : CounterExchange {
            override fun execute(consume: (CounterResponse) -> Unit) {
                val response = client.executeOpen(null, request, null)
                try {
                    consume(CounterResponse(response.code, response.entity?.contentType, response.entity?.content ?: InputStream.nullInputStream()))
                } finally {
                    // 오류·초과 본문을 연결 재사용을 위해 끝까지 읽지 않는다.
                    request.cancel()
                    runCatching { response.close() }
                }
            }
            override fun cancel() { request.cancel() }
        }
    }

    override fun destroy() { client.close() }
}

internal fun counterHttpClient(
    policy: ServingDestinationPolicy,
    properties: ServingRelayProperties,
    tls: org.apache.hc.client5.http.ssl.TlsSocketStrategy? = null,
): org.apache.hc.client5.http.impl.classic.CloseableHttpClient {
    return HttpClients.custom()
        .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
            .setDnsResolver(object : DnsResolver {
                override fun resolve(host: String) = policy.resolve(host)
                override fun resolveCanonicalHostname(host: String) = host
            })
            .apply { if (tls != null) setTlsSocketStrategy(tls) }
            .setMaxConnTotal(properties.maxConnections)
            .setMaxConnPerRoute(properties.maxConnections)
            .setDefaultConnectionConfig(ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(properties.connectSeconds))
                .setSocketTimeout(Timeout.ofSeconds(properties.idleSeconds)).build()).build())
        .setDefaultRequestConfig(RequestConfig.custom()
            .setConnectionRequestTimeout(Timeout.ofSeconds(properties.connectSeconds))
            .setResponseTimeout(Timeout.ofSeconds(properties.idleSeconds)).build())
        .disableRedirectHandling().disableAutomaticRetries().disableCookieManagement()
        .disableAuthCaching().disableContentCompression().build()

}
