package com.duro.kukie.global.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/** 웹 토큰 쿠키 설정. 이름은 agent(`auth.py`)도 읽으므로 바꾸면 함께 바꾼다. */
@ConfigurationProperties(prefix = "auth.cookie")
data class AuthCookieProperties(
    val accessName: String = "kukie_access",
    val refreshName: String = "kukie_refresh",
    val secure: Boolean = true,
)
