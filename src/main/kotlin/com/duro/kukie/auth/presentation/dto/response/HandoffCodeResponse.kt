package com.duro.kukie.auth.presentation.dto.response

data class HandoffCodeResponse(
    val code: String,
    /** 초. 이 안에 앱이 `POST /auth/exchange` 로 바꿔야 한다 */
    val expiresIn: Long,
)
