package com.duro.kukie.chat.domain

import com.fasterxml.jackson.annotation.JsonValue

enum class RunStatus(@get:JsonValue val value: String, val active: Boolean) {
    RUNNING("running", true),
    AWAITING_APPROVAL("awaiting_approval", true),
    RECOVERY_REQUIRED("recovery_required", true),
    COMPLETED("completed", false),
    FAILED("failed", false),
    INTERRUPTED("interrupted", false),
    ;

    companion object {
        val ACTIVE: List<RunStatus> = entries.filter { it.active }

        fun from(value: String): RunStatus =
            entries.firstOrNull { it.value == value } ?: throw IllegalArgumentException("알 수 없는 run 상태: $value")
    }
}
