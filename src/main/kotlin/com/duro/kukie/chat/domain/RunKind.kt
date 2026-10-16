package com.duro.kukie.chat.domain

import com.fasterxml.jackson.annotation.JsonValue

enum class RunKind(@get:JsonValue val value: String) {
    CHAT("chat"),
    MODE_CHANGE("mode_change"),
    ;

    companion object {
        fun from(value: String): RunKind =
            entries.firstOrNull { it.value == value } ?: throw IllegalArgumentException("알 수 없는 run 종류: $value")
    }
}
