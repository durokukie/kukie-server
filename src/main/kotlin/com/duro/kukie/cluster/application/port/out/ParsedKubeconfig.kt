package com.duro.kukie.cluster.application.port.out

import java.security.MessageDigest
import java.util.HexFormat

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
        get() = HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest("$apiServer\n${caData.orEmpty()}".toByteArray(Charsets.UTF_8)),
        )
}
