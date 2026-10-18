package com.duro.kukie.cluster.application.port.out

import com.duro.kukie.cluster.domain.Cluster

/** 자격증명이 toString 으로 로그에 새지 않게 data class 로 두지 않는다. */
class ParsedKubeconfig(
    val contextName: String,
    val apiServer: String,
    val caData: String?,
    val namespace: String,
    val credential: Map<String, String>,
    val insecure: Boolean,
) {
    val fingerprint: String
        get() = Cluster.fingerprintOf(apiServer, caData)
}
