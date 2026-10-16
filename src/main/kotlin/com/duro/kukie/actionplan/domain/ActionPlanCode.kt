package com.duro.kukie.actionplan.domain

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

object ActionPlanCode {

    private val MINUTE = DateTimeFormatter.ofPattern("yyMMdd-HHmm")

    private val UNSAFE = Pattern.compile("[^\\w-]+", Pattern.UNICODE_CHARACTER_CLASS)

    fun minutePrefix(at: LocalDateTime): String = "ap-${MINUTE.format(at)}"

    fun base(at: LocalDateTime, tool: String): String = "${minutePrefix(at)}-${toolPart(tool)}"

    fun next(base: String, taken: Collection<String>): String {
        if (base !in taken) return base
        return generateSequence(2) { it + 1 }.map { "$base-$it" }.first { it !in taken }
    }

    private fun toolPart(tool: String): String =
        UNSAFE.matcher(tool).replaceAll("-").trim('-').ifEmpty { "tool" }
}
