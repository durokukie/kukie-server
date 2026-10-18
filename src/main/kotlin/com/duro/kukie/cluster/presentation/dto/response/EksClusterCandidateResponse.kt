package com.duro.kukie.cluster.presentation.dto.response

import com.duro.kukie.cluster.application.port.out.EksClusterSummary

data class EksClusterCandidateResponse(
    val name: String,
    val region: String,
    val status: String,
    val version: String?,
) {
    companion object {
        fun from(summary: EksClusterSummary) = EksClusterCandidateResponse(
            name = summary.name,
            region = summary.region,
            status = summary.status,
            version = summary.version,
        )
    }
}
