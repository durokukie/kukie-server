package com.duro.kukie.actionplan.domain

import com.fasterxml.jackson.annotation.JsonValue

enum class DryRunStatus(@get:JsonValue val value: String, val passed: Boolean) {
    SUCCEEDED("succeeded", true),
    FAILED("failed", false),
    UNSUPPORTED("unsupported", true),
    ;

    companion object {
        fun fromOrNull(value: String?): DryRunStatus? = entries.firstOrNull { it.value == value }
    }
}
