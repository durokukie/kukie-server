package com.duro.kukie.auth.presentation.dto.response

data class HandoffCodeResponse(
    val code: String,
    /** 초 */
    val expiresIn: Long,
)
