package com.duro.kukie.cluster.infrastructure

import com.duro.kukie.cluster.application.port.out.ApiServerAddressChecker
import com.duro.kukie.cluster.application.port.out.KubeconfigParser
import com.duro.kukie.cluster.application.port.out.ParsedKubeconfig
import com.duro.kukie.cluster.exception.KubeconfigRejectedException
import org.springframework.stereotype.Component
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.error.YAMLException

@Component
class SnakeYamlKubeconfigParser(
    private val apiServerAddressChecker: ApiServerAddressChecker,
) : KubeconfigParser {

    override fun parse(kubeconfig: String, contextName: String?): ParsedKubeconfig {
        val document = load(kubeconfig)
        ensure(document is Map<*, *>, "kubeconfig 형식이 아닙니다")
        document as Map<*, *>

        val contexts = named(document["contexts"], "context")
        val chosen = contextName?.takeIf { it.isNotEmpty() }
            ?: (document["current-context"] as? String)?.takeIf { it.isNotEmpty() }
            ?: contexts.keys.singleOrNull()
            ?: reject("context 가 여럿입니다. 하나를 골라 주세요: ${contexts.keys.sorted().joinToString(", ")}")
        ensure(chosen in contexts, "그런 context 가 없습니다: $chosen (있는 것: ${contexts.keys.sorted().joinToString(", ")})")
        val context = contexts.getValue(chosen)

        val clusters = named(document["clusters"], "cluster")
        val users = named(document["users"], "user")
        val clusterKey = context["cluster"]
        val userKey = context["user"]
        ensure(clusterKey is String && clusterKey in clusters, "context 가 가리키는 cluster 가 없습니다: $clusterKey")
        ensure(userKey is String && userKey in users, "context 가 가리키는 user 가 없습니다: $userKey")
        val cluster = clusters.getValue(clusterKey as String)
        val user = users.getValue(userKey as String)

        ensure(
            !cluster.containsKey("certificate-authority"),
            "파일 경로(certificate-authority)는 쓸 수 없습니다 — 인라인 값만 받습니다",
        )
        val rawServer = cluster["server"]
        ensure(rawServer is String && rawServer.isNotBlank(), "클러스터 주소(server)가 없습니다")
        val server = (rawServer as String).trim()
        apiServerAddressChecker.rejectionOf(server)?.let(::reject)

        val insecure = cluster["insecure-skip-tls-verify"] == true
        val ca = cluster["certificate-authority-data"]
        ensure(!(insecure && ca != null), "CA 인증서와 insecure-skip-tls-verify 를 함께 쓸 수 없습니다 — 하나만 남기세요")
        // CA 가 없으면 공인 인증서 서버(GKE DNS 엔드포인트 등)로 보고 agent 가 기본 신뢰 저장소로 검증한다.
        val caData = ca?.let { base64(it, "certificate-authority-data") }

        val namespace = (context["namespace"] as? String)?.trim().orEmpty()

        return ParsedKubeconfig(
            contextName = chosen,
            apiServer = server.trimEnd('/'),
            caData = caData,
            namespace = namespace.ifEmpty { DEFAULT_NAMESPACE },
            credential = credential(user),
            insecure = insecure,
        )
    }

    private fun load(text: String): Any? =
        try {
            Yaml(SafeConstructor(LoaderOptions())).load<Any?>(text)
        } catch (exception: YAMLException) {
            reject("kubeconfig 를 읽을 수 없습니다 (YAML 오류): ${exception.message}")
        }

    private fun named(entries: Any?, kind: String): Map<String, Map<*, *>> {
        ensure(entries is List<*>, "kubeconfig 에 $kind 목록이 없습니다")
        val found = linkedMapOf<String, Map<*, *>>()
        for (entry in entries as List<*>) {
            if (entry !is Map<*, *>) continue
            val name = entry["name"]
            val body = entry[kind]
            if (name is String && body is Map<*, *>) found[name] = body
        }
        ensure(found.isNotEmpty(), "kubeconfig 에 $kind 가 없습니다")
        return found
    }

    private fun credential(user: Map<*, *>): Map<String, String> {
        ensure(
            !user.containsKey("exec"),
            "외부 인증 프로그램(exec)을 쓰는 kubeconfig 는 받지 않습니다. OKE·GKE·EKS 와 Entra ID 연동 AKS 가 만들어 주는 " +
                "kubeconfig 가 이 방식입니다(oci·gke-gcloud-auth-plugin·aws·kubelogin 이 짧은 토큰을 그때그때 만듭니다). 클러스터에 Kukie 용 " +
                "ServiceAccount 토큰을 만들어 토큰 kubeconfig 로 등록하세요: kubectl apply -f $SA_TOKEN_TEMPLATE_URL " +
                "— 권한 고르기와 kubeconfig 만드는 명령은 그 파일 맨 위 주석에 있습니다",
        )
        ensure(!user.containsKey("auth-provider"), "auth-provider 방식은 아직 지원하지 않습니다")
        for (pathField in listOf("client-certificate", "client-key", "tokenFile")) {
            ensure(!user.containsKey(pathField), "파일 경로($pathField)는 쓸 수 없습니다 — 인라인 값만 받습니다")
        }

        val token = user["token"]
        if (token is String && token.isNotBlank()) {
            return mapOf("token" to token.trim())
        }

        val certificate = user["client-certificate-data"]
        val key = user["client-key-data"]
        if (certificate != null || key != null) {
            return mapOf(
                "client_certificate_data" to base64(certificate, "client-certificate-data"),
                "client_key_data" to base64(key, "client-key-data"),
            )
        }

        ensure(
            !user.containsKey("username") && !user.containsKey("password"),
            "아이디·비밀번호 방식은 지원하지 않습니다 (토큰 또는 인증서를 쓰세요)",
        )
        reject("kubeconfig 에 자격증명(토큰 또는 client certificate)이 없습니다")
    }

    private fun base64(value: Any?, field: String): String {
        ensure(value is String && value.isNotBlank(), "$field 값이 비어 있습니다")
        ensure(STRICT_BASE64.matches(value as String), "$field 가 base64 가 아닙니다")
        return value
    }

    private fun ensure(condition: Boolean, message: String) {
        if (!condition) reject(message)
    }

    private fun reject(message: String): Nothing = throw KubeconfigRejectedException(message)

    companion object {
        private const val DEFAULT_NAMESPACE = "default"

        /** kukie-infra terraform/github-actions-foundation(customer-templates.tf)이 게시하는 파일이다. */
        private const val SA_TOKEN_TEMPLATE_URL =
            "https://kukie-templates-320343233643-ap-northeast-2.s3.ap-northeast-2.amazonaws.com/kukie-access/sa-token-v1.yaml"

        private val STRICT_BASE64 = Regex("(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?")
    }
}
