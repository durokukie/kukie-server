package com.duro.kukie.global.domain

@JvmInline
value class Email private constructor(val value: String) {
    override fun toString(): String = value

    companion object {
        operator fun invoke(value: String): Email = Email(value.trim().lowercase())
    }
}
