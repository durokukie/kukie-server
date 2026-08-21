package com.duro.kukie.auth.dto.response

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
)
