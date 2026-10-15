package com.duro.kukie.actionplan.domain

import com.fasterxml.jackson.annotation.JsonValue

enum class PlanRisk(@get:JsonValue val value: String) {
    CAUTION("caution"),
    DESTRUCTIVE("destructive"),
    ;

    companion object {
        fun from(value: String): PlanRisk =
            entries.firstOrNull { it.value == value } ?: throw IllegalArgumentException("알 수 없는 위험도: $value")
    }
}
