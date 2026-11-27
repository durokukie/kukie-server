package com.duro.kukie.report.infrastructure

import com.duro.kukie.cluster.ClusterFixture
import com.duro.kukie.cluster.application.port.out.ClusterApi
import com.duro.kukie.cluster.domain.Cluster
import com.duro.kukie.report.application.port.out.ClusterMetrics
import com.duro.kukie.report.application.port.out.ClusterMetricsResult
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.net.URLDecoder
import java.time.Instant
import java.util.UUID

class PrometheusClusterMetricsReaderTest {

    private val mapper = JsonMapper.builder().build()
    private val cluster = ClusterFixture.cluster(registeredBy = UUID.randomUUID(), teamId = UUID.randomUUID())
    private val from = Instant.parse("2026-11-02T00:00:00Z")
    private val to = Instant.parse("2026-11-09T00:00:00Z")

    /** 요청 경로(쿼리를 푼 것)를 받아 답을 준다. */
    private class FakeApi(private val answer: (String) -> ClusterApi.Response) : ClusterApi {
        val paths = mutableListOf<String>()

        override fun <T> read(cluster: Cluster, block: (ClusterApi.Session) -> T): T = block { path ->
            val decoded = URLDecoder.decode(path, Charsets.UTF_8)
            paths += decoded
            answer(decoded)
        }
    }

    @Test
    fun `Prometheus 서비스를 찾아 프록시로 일주일치 지표를 읽는다`() {
        // given
        val api = FakeApi { path ->
            when {
                path.startsWith("/api/v1/services?labelSelector=operated-prometheus=true") -> services("monitoring", "prometheus-operated", "web" to 9090)
                "query=count(kube_node_info)" in path -> vector("3")
                "kube_node_status_condition" in path -> vector("0.99984")
                "job=\"apiserver\"" in path -> vector("1")
                "kube_deployment_status_replicas_available" in path -> vector("NaN")
                "query=avg_over_time((1 - avg(rate(node_cpu_seconds_total" in path -> vector("0.342")
                "query=max_over_time((1 - avg(rate(node_cpu_seconds_total" in path -> vector("0.708")
                "query=avg_over_time((1 - sum(node_memory_MemAvailable_bytes)" in path -> vector("0.52")
                "query=max_over_time((1 - sum(node_memory_MemAvailable_bytes)" in path -> vector("0.68")
                "query=sum(increase(kube_pod_container_status_restarts_total" in path -> vector("12.4")
                "query=topk(3," in path -> vector("7.2", "namespace" to "web", "pod" to "api-7d9f") + vector("5", "namespace" to "web", "pod" to "worker-1")
                else -> ClusterApi.Response(404, "")
            }
        }

        // when
        val result = PrometheusClusterMetricsReader(api, mapper).read(cluster, from, to)

        // then
        result shouldBe ClusterMetricsResult.Available(
            ClusterMetrics(
                nodes = 3,
                nodeReady = 0.99984,
                apiServerUp = 1.0,
                workloadsAvailable = null,
                cpuAverage = 0.342,
                cpuPeak = 0.708,
                memoryAverage = 0.52,
                memoryPeak = 0.68,
                restarts = 12,
                topRestarts = listOf(ClusterMetrics.PodRestarts("web", "api-7d9f", 7), ClusterMetrics.PodRestarts("web", "worker-1", 5)),
            ),
        )
        val query = api.paths.first { "kube_node_status_condition" in it }
        query shouldStartWith "/api/v1/namespaces/monitoring/services/prometheus-operated:web/proxy/api/v1/query?query="
        query shouldContain "[604800s]"
        query shouldContain "&time=${to.epochSecond}"
    }

    @Test
    fun `모든 namespace 를 볼 권한이 없으면 흔히 쓰는 namespace 에서 찾는다`() {
        // given
        val api = FakeApi { path ->
            when {
                path.startsWith("/api/v1/services?") -> ClusterApi.Response(403, "")
                path.startsWith("/api/v1/namespaces/monitoring/services?labelSelector=app.kubernetes.io/name=prometheus,app.kubernetes.io/component=server") ->
                    services("monitoring", "prometheus-server", "http" to 80)
                path.startsWith("/api/v1/namespaces/monitoring/services?") -> services()
                "query=count(kube_node_info)" in path -> vector("2")
                else -> empty()
            }
        }

        // when
        val result = PrometheusClusterMetricsReader(api, mapper).read(cluster, from, to)

        // then
        (result as ClusterMetricsResult.Available).metrics.nodes shouldBe 2
        api.paths.map { it.substringBefore("?") } shouldContain "/api/v1/namespaces/monitoring/services/prometheus-server:http/proxy/api/v1/query"
    }

    @Test
    fun `Prometheus 가 없거나 클러스터를 읽지 못하면 그 까닭을 돌려준다`() {
        // given
        val none = FakeApi { services() }
        val broken = FakeApi { error("connection refused") }

        // when & then
        PrometheusClusterMetricsReader(none, mapper).read(cluster, from, to) shouldBe ClusterMetricsResult.NoPrometheus
        PrometheusClusterMetricsReader(broken, mapper).read(cluster, from, to) shouldBe ClusterMetricsResult.Unreadable
    }

    private fun services(namespace: String? = null, name: String? = null, vararg ports: Pair<String, Int>) = ClusterApi.Response(
        200,
        mapper.writeValueAsString(
            mapOf(
                "items" to listOfNotNull(
                    name?.let {
                        mapOf(
                            "metadata" to mapOf("namespace" to namespace, "name" to it),
                            "spec" to mapOf("ports" to ports.map { (portName, port) -> mapOf("name" to portName, "port" to port) }),
                        )
                    },
                ),
            ),
        ),
    )

    private fun vector(value: String, vararg labels: Pair<String, String>) = ClusterApi.Response(
        200,
        mapper.writeValueAsString(
            mapOf("status" to "success", "data" to mapOf("resultType" to "vector", "result" to listOf(mapOf("metric" to mapOf(*labels), "value" to listOf(1.0, value))))),
        ),
    )

    private fun empty() = ClusterApi.Response(200, """{"status":"success","data":{"resultType":"vector","result":[]}}""")

    /** 표본 둘을 한 답으로 합친다. */
    private operator fun ClusterApi.Response.plus(other: ClusterApi.Response): ClusterApi.Response {
        val results = listOf(this, other).flatMap { ((mapper.readValue(it.body, Map::class.java)["data"] as Map<*, *>)["result"] as List<*>) }

        return ClusterApi.Response(200, mapper.writeValueAsString(mapOf("status" to "success", "data" to mapOf("resultType" to "vector", "result" to results))))
    }
}
