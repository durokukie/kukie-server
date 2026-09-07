package com.duro.kukie.auth.domain

import com.fasterxml.jackson.annotation.JsonValue

enum class OAuthProvider(@get:JsonValue val value: String) {
    GITHUB("github"),
    GOOGLE("google"),
    ;

    companion object {
        fun from(value: String): OAuthProvider = entries.first { it.value == value }
    }
}
