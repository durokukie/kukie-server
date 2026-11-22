package com.duro.kukie.auth.presentation.dto.response

data class OAuthLogInResponse(
    val accessToken: String,
    val refreshToken: String,
    val signedUp: Boolean,
) {
    companion object {
        fun of(tokens: TokenResponse, signedUp: Boolean) =
            OAuthLogInResponse(tokens.accessToken, tokens.refreshToken, signedUp)
    }
}
