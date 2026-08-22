package com.duro.kukie.auth.presentation.dto.response

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
)
