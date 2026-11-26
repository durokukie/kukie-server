package com.duro.kukie.report.infrastructure

import com.duro.kukie.cluster.application.port.out.ClusterApi
import com.duro.kukie.cluster.domain.Cluster
import com.duro.kukie.global.util.logger
import com.duro.kukie.report.application.port.out.ClusterMetrics
import com.duro.kukie.report.application.port.out.ClusterMetricsReader
import com.duro.kukie.report.application.port.out.ClusterMetricsResult
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.net.URLEncoder
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToLong

/**
 * 클러스터 안의 Prometheus 를 API 서버의 서비스 프록시로 읽는다. 지표 이름은 kube-state-metrics 와 node-exporter 의 것이다
 * (kube-prometheus-stack 이 기본으로 깐다). 그 둘이 없는 Prometheus 면 해당 항목만 비운다.
 */
@Component
class PrometheusClusterMetricsReader(
    private val clusterApi: ClusterApi,
    private val objectMapper: ObjectMapper,
) : ClusterMetricsReader {

    private val log = logger()

    private class Target(val namespace: String, val service: String, val port: String)

    private class Forbidden : RuntimeException()

    private class Sample(val labels: Map<*, *>, val value: Double)

    override fun read(cluster: Cluster, from: Instant, to: Instant): ClusterMetricsResult =
        try {
            clusterApi.read(cluster) { api ->
                val target = find(api, cluster) ?: return@read ClusterMetricsResult.NoPrometheus

                ClusterMetricsResult.Available(metrics(api, target, Duration.between(from, to).seconds.coerceAtLeast(MIN_RANGE_SECONDS), to))
            }
        } catch (exception: Exception) {
            log.warn("클러스터 운영 지표를 읽지 못했습니다: cluster={} reason={}", cluster.id, exception.toString())
            ClusterMetricsResult.Unreadable
        }

    /** 모든 namespace 를 볼 권한이 없으면 흔히 쓰는 namespace 와 등록한 namespace 만 본다. */
    private fun find(api: ClusterApi.Session, cluster: Cluster): Target? {
        val scopes = try {
            return SELECTORS.firstNotNullOfOrNull { services(api, "/api/v1/services", it) }
        } catch (_: Forbidden) {
            listOf(MONITORING, cluster.defaultNamespace).distinct()
        }

        return scopes.firstNotNullOfOrNull { namespace ->
            SELECTORS.firstNotNullOfOrNull {
                try {
                    services(api, "/api/v1/namespaces/$namespace/services", it)
                } catch (_: Forbidden) {
                    null
                }
            }
        }
    }

    private fun services(api: ClusterApi.Session, path: String, selector: String): Target? {
        val response = api.get("$path?labelSelector=${encode(selector)}&limit=$SERVICE_LIMIT")
        if (response.status == 403) throw Forbidden()
        check(response.status == 200) { "서비스 목록을 읽지 못했습니다: ${response.status}" }

        return (objectMapper.readValue(response.body, Map::class.java)["items"] as? List<*>).orEmpty()
            .filterIsInstance<Map<*, *>>()
            .firstNotNullOfOrNull(::target)
    }

    private fun target(service: Map<*, *>): Target? {
        val metadata = service["metadata"] as? Map<*, *> ?: return null
        val namespace = metadata["namespace"] as? String ?: return null
        val name = metadata["name"] as? String ?: return null
        val ports = ((service["spec"] as? Map<*, *>)?.get("ports") as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
        val port = PORT_NAMES.firstNotNullOfOrNull { wanted -> ports.firstOrNull { it["name"] == wanted } }
            ?: ports.firstOrNull { (it["port"] as? Number)?.toInt() == PROMETHEUS_PORT }
            ?: ports.firstOrNull()
            ?: return null

        val portName = (port["name"] as? String)?.takeIf { it.isNotEmpty() } ?: (port["port"] as? Number)?.toInt()?.toString() ?: return null

        // 클러스터가 준 이름을 요청 경로에 넣는다 — 이름 모양이 아니면 쓰지 않는다
        return Target(namespace, name, portName).takeIf { listOf(namespace, name, portName).all(NAME::matches) }
    }

    private fun metrics(api: ClusterApi.Session, target: Target, rangeSeconds: Long, at: Instant): ClusterMetrics {
        val deadline = System.nanoTime() + BUDGET.toNanos()
        val range = "${rangeSeconds}s"

        fun query(promql: String): List<Sample> {
            if (System.nanoTime() > deadline) return emptyList()
            val path = "/api/v1/namespaces/${target.namespace}/services/${target.service}:${target.port}/proxy/api/v1/query"
            val response = api.get("$path?query=${encode(promql)}&time=${at.epochSecond}&timeout=${QUERY_TIMEOUT_SECONDS}s")
            if (response.status != 200) {
                log.warn("Prometheus 쿼리가 실패했습니다: status={} query={}", response.status, promql)
                return emptyList()
            }

            return samples(objectMapper.readValue(response.body, Map::class.java))
        }

        fun value(promql: String): Double? = query(promql).firstOrNull()?.value
        fun ratio(promql: String): Double? = value(promql)?.coerceIn(0.0, 1.0)

        val cpu = "(1 - avg(rate(node_cpu_seconds_total{mode=\"idle\"}[$STEP])))[$range:$STEP]"
        val memory = "(1 - sum(node_memory_MemAvailable_bytes) / sum(node_memory_MemTotal_bytes))[$range:$STEP]"
        val restarts = "increase(kube_pod_container_status_restarts_total[$range])"

        return ClusterMetrics(
            nodes = value("count(kube_node_info)")?.toInt(),
            nodeReady = ratio("avg(avg_over_time(kube_node_status_condition{condition=\"Ready\",status=\"true\"}[$range]))"),
            apiServerUp = ratio("avg(avg_over_time(up{job=\"apiserver\"}[$range]))"),
            workloadsAvailable = ratio("avg_over_time((sum(kube_deployment_status_replicas_available) / sum(kube_deployment_spec_replicas))[$range:$STEP])"),
            cpuAverage = ratio("avg_over_time($cpu)"),
            cpuPeak = ratio("max_over_time($cpu)"),
            memoryAverage = ratio("avg_over_time($memory)"),
            memoryPeak = ratio("max_over_time($memory)"),
            restarts = value("sum($restarts)")?.roundToLong(),
            topRestarts = query("topk($TOP_RESTARTS, sum by (namespace, pod) ($restarts) >= 1)")
                .sortedByDescending { it.value }
                .mapNotNull { sample ->
                    val namespace = sample.labels["namespace"] as? String ?: return@mapNotNull null
                    val pod = sample.labels["pod"] as? String ?: return@mapNotNull null
                    ClusterMetrics.PodRestarts(namespace, pod, sample.value.roundToLong())
                },
        )
    }

    /** 값이 없는 구간(NaN)은 뺀다. */
    private fun samples(answer: Map<*, *>): List<Sample> {
        if (answer["status"] != "success") return emptyList()
        return ((answer["data"] as? Map<*, *>)?.get("result") as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().mapNotNull { series ->
            val value = ((series["value"] as? List<*>)?.getOrNull(1) as? String)?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return@mapNotNull null
            Sample(series["metric"] as? Map<*, *> ?: emptyMap<Any, Any>(), value)
        }
    }

    private fun encode(text: String): String = URLEncoder.encode(text, Charsets.UTF_8)

    companion object {
        // Prometheus Operator 가 만드는 서비스, 커뮤니티 차트의 서버, kube-prometheus 의 순서로 찾는다
        private val SELECTORS = listOf(
            "operated-prometheus=true",
            "app.kubernetes.io/name=prometheus,app.kubernetes.io/component=server",
            "app.kubernetes.io/name=prometheus",
        )
        private val PORT_NAMES = listOf("http-web", "web", "http")
        private val NAME = Regex("[a-z0-9]([-a-z0-9.]{0,251}[a-z0-9])?")
        private const val PROMETHEUS_PORT = 9090
        private const val MONITORING = "monitoring"
        private const val SERVICE_LIMIT = 20
        private const val STEP = "5m"
        private const val TOP_RESTARTS = 3
        private const val MIN_RANGE_SECONDS = 300L
        private const val QUERY_TIMEOUT_SECONDS = 15

        // 쿼리가 느린 Prometheus 때문에 보고서가 멈춘 것으로 보이지 않게 한다
        private val BUDGET: Duration = Duration.ofSeconds(60)
    }
}
