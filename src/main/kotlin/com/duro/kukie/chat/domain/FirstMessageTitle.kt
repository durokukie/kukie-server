package com.duro.kukie.chat.domain

object FirstMessageTitle {

    private const val LIMIT = 30

    private val WHITESPACE = Regex("(?U)\\s+")

    fun titleable(text: String?): Boolean {
        val normalized = normalize(text.orEmpty())
        return normalized.isNotEmpty() && !normalized.startsWith("/mode")
    }

    fun of(text: String): String = normalize(text).takeCodePoints(LIMIT).trim()

    private fun normalize(text: String): String =
        text.split(WHITESPACE).filter { it.isNotEmpty() }.joinToString(" ")

    private fun String.takeCodePoints(count: Int): String {
        if (codePointCount(0, length) <= count) return this
        return substring(0, offsetByCodePoints(0, count))
    }
}
