package com.duro.kukie.chat.application

import com.duro.kukie.global.exception.BusinessException
import com.duro.kukie.global.exception.GlobalErrorCode

enum class ContextPurpose(val value: String) {
    CHAT("chat"),
    APPROVE("approve"),
    ;

    companion object {
        fun from(value: String): ContextPurpose =
            entries.firstOrNull { it.value == value } ?: throw BusinessException(GlobalErrorCode.BAD_REQUEST)
    }
}
