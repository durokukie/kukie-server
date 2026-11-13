package com.duro.kukie.chat.domain

object FirstMessageTitle {

    private const val LIMIT = 30

    private val WHITESPACE = Regex("(?U)\\s+")

    private val QUOTES = charArrayOf('"', '\'', '“', '”', '‘', '’', '`')

    fun titleable(text: String?): Boolean {
        val normalized = normalize(text.orEmpty())
        return normalized.isNotEmpty() && !normalized.startsWith("/mode")
    }

    fun of(text: String): String = normalize(text).takeCodePoints(LIMIT).trim()

    /** 모델이 지은 제목을 한 줄로 다듬는다. 따옴표만 감싼 것은 벗기고, 비거나 기본 제목이면 null (첫 마디로 짓는다). */
    fun suggested(title: String?): String? {
        val normalized = normalize(title.orEmpty()).trim(*QUOTES).trim()
        if (normalized.isEmpty() || normalized == ChatSession.DEFAULT_TITLE) return null
        return normalized.takeCodePoints(LIMIT).trim()
    }

    private fun normalize(text: String): String =
        text.split(WHITESPACE).filter { it.isNotEmpty() }.joinToString(" ")

    private fun String.takeCodePoints(count: Int): String {
        if (codePointCount(0, length) <= count) return this
        return substring(0, offsetByCodePoints(0, count))
    }
}
