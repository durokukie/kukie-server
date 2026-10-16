package com.duro.kukie.cluster.domain

import com.fasterxml.jackson.annotation.JsonValue

enum class ClusterStatus(@get:JsonValue val value: String) {
    CONNECTED("connected"),
    DISCONNECTED("disconnected"),
    AUTH_EXPIRED("auth_expired"),
    ;

    companion object {
        fun from(value: String): ClusterStatus =
            entries.firstOrNull { it.value == value } ?: throw IllegalArgumentException("알 수 없는 클러스터 상태: $value")
    }
}
