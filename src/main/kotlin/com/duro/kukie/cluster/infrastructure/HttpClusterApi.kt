package com.duro.kukie.cluster.infrastructure

import com.duro.kukie.cluster.application.EksCredential
import com.duro.kukie.cluster.application.port.out.ApiServerAddressChecker
import com.duro.kukie.cluster.application.port.out.ClusterApi
import com.duro.kukie.cluster.application.port.out.CredentialCipher
import com.duro.kukie.cluster.application.port.out.EksAccessGateway
import com.duro.kukie.cluster.domain.Cluster
import com.duro.kukie.cluster.domain.ClusterProvider
import org.springframework.boot.ssl.SslBundle
import org.springframework.boot.ssl.pem.PemSslStoreBundle
import org.springframework.boot.ssl.pem.PemSslStoreDetails
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64
import javax.net.ssl.SSLContext

@Component
class HttpClusterApi(
    private val credentialCipher: CredentialCipher,
    private val eksAccessGateway: EksAccessGateway,
    private val apiServerAddressChecker: ApiServerAddressChecker,
) : ClusterApi {

    override fun <T> read(cluster: Cluster, block: (ClusterApi.Session) -> T): T {
        // 등록한 뒤에 이름이 사설 주소로 바뀌었을 수 있다
        apiServerAddressChecker.rejectionOf(cluster.apiServer)?.let { error(it) }
        // 인증서를 확인하지 않는 연결로는 자격증명을 보내지 않는다
        check(!cluster.insecure) { "TLS 검증을 끈 클러스터는 서버가 직접 읽지 않습니다" }

        val stored = credentialCipher.decrypt(cluster.credential)
        val token = when (cluster.provider) {
            ClusterProvider.EKS -> EksCredential.from(stored).let { eksAccessGateway.issueToken(it.target, it.region, it.clusterName) }
            ClusterProvider.GENERIC -> stored[TOKEN]
        }
        val base = cluster.apiServer.trimEnd('/')

        return HttpClient.newBuilder()
            .sslContext(sslContext(cluster, stored))
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()
            .use { http ->
                block { path ->
                    val request = HttpRequest.newBuilder(URI(base + path)).timeout(READ_TIMEOUT).GET()
                    token?.let { request.header("Authorization", "Bearer $it") }
                    val response = http.send(request.build(), HttpResponse.BodyHandlers.ofInputStream())
                    val body = response.body().use { it.readNBytes(BODY_LIMIT + 1) }
                    check(body.size <= BODY_LIMIT) { "클러스터의 응답이 너무 큽니다" }

                    ClusterApi.Response(response.statusCode(), body.decodeToString())
                }
            }
    }

    private fun sslContext(cluster: Cluster, stored: Map<String, String>): SSLContext {
        val certificate = stored[CLIENT_CERTIFICATE]
        val key = stored[CLIENT_KEY]
        val keyStore = if (certificate != null && key != null) PemSslStoreDetails.forCertificate(pem(certificate)).withPrivateKey(pem(key)) else null
        // CA 가 없으면 JDK 가 믿는 공인 인증서로 확인한다
        val trustStore = cluster.caData?.let { PemSslStoreDetails.forCertificate(pem(it)) }

        return SslBundle.of(PemSslStoreBundle(keyStore, trustStore)).createSslContext()
    }

    private fun pem(base64: String): String = Base64.getDecoder().decode(base64).decodeToString()

    companion object {
        private const val TOKEN = "token"
        private const val CLIENT_CERTIFICATE = "client_certificate_data"
        private const val CLIENT_KEY = "client_key_data"
        private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(5)
        private val READ_TIMEOUT: Duration = Duration.ofSeconds(20)
        private const val BODY_LIMIT = 1024 * 1024
    }
}
