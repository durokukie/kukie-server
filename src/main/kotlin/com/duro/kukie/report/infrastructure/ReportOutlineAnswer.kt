package com.duro.kukie.report.infrastructure

import com.duro.kukie.report.application.clipped
import com.duro.kukie.report.application.port.out.ReportOutline
import com.duro.kukie.report.application.port.out.ReportSource

/** AI 에 요구하는 답의 모양과 그 답을 읽는 법. 제공사가 달라도 같다. */
internal object ReportOutlineAnswer {

    private const val MAX_TASKS = 20
    private const val MAX_FOLLOW_UPS = 3
    private const val TITLE_LIMIT = 60
    private const val SUMMARY_LIMIT = 600
    private const val TASK_SUMMARY_LIMIT = 300
    private const val FOLLOW_UP_LIMIT = 200

    private val STRING = mapOf("type" to "string")
    private val INTEGER = mapOf("type" to "integer")

    val SCHEMA: Map<String, Any> = schema(
        "summary" to STRING,
        "tasks" to array(
            schema(
                "title" to STRING,
                "summary" to STRING,
                "turns" to array(schema("conversation" to INTEGER, "seq" to INTEGER)),
                "followUps" to array(STRING),
            ),
        ),
    )

    /** 대화 번호는 ReportOutlinePrompt 가 매긴 순서다. 모르는 번호의 턴은 버린다. */
    fun read(answer: Map<*, *>, source: ReportSource): ReportOutline {
        val conversationIds = source.conversations.map { it.conversation.id }
        val tasks = (answer["tasks"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().take(MAX_TASKS).mapNotNull { task ->
            val title = text(task["title"], TITLE_LIMIT) ?: return@mapNotNull null
            val turns = (task["turns"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().mapNotNull { turn ->
                val conversationId = (turn["conversation"] as? Number)?.let { conversationIds.getOrNull(it.toInt() - 1) }
                val seq = (turn["seq"] as? Number)?.toInt()
                if (conversationId == null || seq == null) null else ReportOutline.TurnRef(conversationId, seq)
            }
            val followUps = (task["followUps"] as? List<*>).orEmpty().mapNotNull { text(it, FOLLOW_UP_LIMIT) }.take(MAX_FOLLOW_UPS)

            ReportOutline.Task(title, text(task["summary"], TASK_SUMMARY_LIMIT), turns, followUps)
        }

        return ReportOutline(text(answer["summary"], SUMMARY_LIMIT), tasks)
    }

    private fun text(value: Any?, limit: Int): String? =
        (value as? String)?.trim()?.takeIf { it.isNotEmpty() }?.clipped(limit)

    private fun schema(vararg properties: Pair<String, Any>) = mapOf(
        "type" to "object",
        "properties" to mapOf(*properties),
        "required" to properties.map { it.first },
        "additionalProperties" to false,
    )

    private fun array(items: Any) = mapOf("type" to "array", "items" to items)
}
