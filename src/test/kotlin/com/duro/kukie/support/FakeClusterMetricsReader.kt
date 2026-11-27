package com.duro.kukie.support

import com.duro.kukie.cluster.domain.Cluster
import com.duro.kukie.report.application.port.out.ClusterMetricsReader
import com.duro.kukie.report.application.port.out.ClusterMetricsResult
import java.time.Instant
import java.util.UUID

class FakeClusterMetricsReader : ClusterMetricsReader, Resettable {

    /** 기본은 Prometheus 가 없는 클러스터다. */
    var result: ClusterMetricsResult = ClusterMetricsResult.NoPrometheus

    val asked = mutableListOf<UUID>()

    override fun read(cluster: Cluster, from: Instant, to: Instant): ClusterMetricsResult {
        asked += cluster.id
        return result
    }

    override fun clear() {
        result = ClusterMetricsResult.NoPrometheus
        asked.clear()
    }
}
