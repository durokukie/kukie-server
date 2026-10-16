package com.duro.kukie.global.util

/** Postgres jsonb 는 문자열 안의 NUL 을 받지 않아 저장 전에 U+FFFD 로 바꾼다. */
@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.withoutNul(): Map<String, Any?> = jsonWithoutNul(this) as Map<String, Any?>

@Suppress("UNCHECKED_CAST")
fun List<Any?>.withoutNul(): List<Any?> = jsonWithoutNul(this) as List<Any?>

fun String.withoutNul(): String = replace(NUL, REPLACEMENT)

private fun jsonWithoutNul(value: Any?): Any? = when (value) {
    is String -> value.withoutNul()
    is Map<*, *> -> value.entries.associate { (key, item) -> jsonWithoutNul(key) to jsonWithoutNul(item) }
    is List<*> -> value.map(::jsonWithoutNul)
    else -> value
}

private const val NUL = '\u0000'
private const val REPLACEMENT = '�'
