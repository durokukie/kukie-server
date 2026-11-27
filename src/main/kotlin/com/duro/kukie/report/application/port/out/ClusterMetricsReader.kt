package com.duro.kukie.report.application.port.out

import com.duro.kukie.cluster.domain.Cluster
import java.time.Instant

interface ClusterMetricsReader {

    /** 기간 동안의 운영 지표. 못 읽어도 던지지 않고 그 까닭을 돌려준다. */
    fun read(cluster: Cluster, from: Instant, to: Instant): ClusterMetricsResult
}

sealed interface ClusterMetricsResult {

    data class Available(val metrics: ClusterMetrics) : ClusterMetricsResult

    /** 클러스터에 Prometheus 가 없다. */
    data object NoPrometheus : ClusterMetricsResult

    /** 권한이 없거나 닿지 못했다. */
    data object Unreadable : ClusterMetricsResult
}

/** 비율은 0~1 이다. 못 읽은 항목은 null 이다. */
data class ClusterMetrics(
    val nodes: Int?,
    val nodeReady: Double?,
    val apiServerUp: Double?,
    val workloadsAvailable: Double?,
    val cpuAverage: Double?,
    val cpuPeak: Double?,
    val memoryAverage: Double?,
    val memoryPeak: Double?,
    val restarts: Long?,
    val topRestarts: List<PodRestarts>,
) {
    data class PodRestarts(val namespace: String, val pod: String, val count: Long)
}
