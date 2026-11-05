package com.duro.kukie.chat.presentation.dto.response

import com.duro.kukie.actionplan.domain.ActionPlan
import com.duro.kukie.actionplan.domain.PlanFailureReason
import com.duro.kukie.actionplan.domain.PlanStatus
import com.duro.kukie.chat.domain.ChatRun
import com.duro.kukie.chat.domain.ChatSession
import com.duro.kukie.chat.domain.ChatVisibility
import com.duro.kukie.chat.domain.RunKind
import com.duro.kukie.chat.domain.RunStatus
import com.duro.kukie.chat.domain.RunTurn
import com.duro.kukie.cluster.domain.Cluster
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

data class WorklogResponse(
    val schema: String,
    val conversation: Conversation,
    val environment: Environment,
    val people: List<Person>,
    val turns: List<Turn>,
) {
    data class Conversation(val id: UUID, val title: String, val shared: Boolean, val ownerId: UUID)

    data class Environment(
        val clusterId: UUID,
        val clusterName: String,
        val context: String,
        val namespace: String,
        val fingerprint: String,
    )

    data class Person(val id: UUID, val name: String)

    data class Turn(
        val seq: Int,
        val kind: RunKind,
        val mode: String,
        val status: RunStatus,
        val requestedBy: UUID?,
        val request: String?,
        val startedAt: Instant,
        val finishedAt: Instant?,
        val steps: List<Step>,
        val changes: List<Change>,
        val answer: Answer?,
        val error: Error?,
    )

    data class Step(val label: String?, val access: String?, val command: String?, val output: String?)

    data class Change(
        val code: String,
        val tool: String,
        val intent: String?,
        val args: Any?,
        val command: List<Any?>,
        val target: Any?,
        val risk: String,
        val status: PlanStatus,
        val failureReason: PlanFailureReason?,
        val dryRun: Map<String, Any?>?,
        val decision: Decision?,
        val result: Map<String, Any?>?,
        val pullRequest: Any?,
        val appliedAt: Instant?,
    )

    data class Decision(val approved: Boolean, val by: UUID?, val at: String?)

    data class Answer(val narration: String?, val finding: Finding?, val suggestedNextAction: String?)

    data class Finding(val cause: String?, val evidence: String?, val recommendation: String?)

    data class Error(val code: String?, val message: String?)

    companion object {
        const val SCHEMA = "kukie.worklog/v1"
        const val OUTPUT_LIMIT = 4000

        fun of(
            session: ChatSession,
            cluster: Cluster,
            people: List<Person>,
            runs: List<RunTurn>,
            plansByRun: Map<UUID, List<ActionPlan>>,
        ) = WorklogResponse(
            schema = SCHEMA,
            conversation = Conversation(session.id, session.title, session.visibility == ChatVisibility.SHARED, session.userId),
            environment = Environment(cluster.id, cluster.name, session.contextName, session.namespace, session.clusterFingerprint),
            people = people,
            turns = runs.map { turn(it, plansByRun[it.id].orEmpty()) },
        )

        private fun turn(run: RunTurn, plans: List<ActionPlan>): Turn {
            val payload = run.responsePayload
            val response = payload?.get("response") as? Map<*, *>

            return Turn(
                seq = run.turnNo,
                kind = run.kind,
                mode = run.mode,
                status = run.status,
                requestedBy = run.requestedBy,
                request = run.inputText,
                startedAt = run.startedAt.utc(),
                finishedAt = run.finishedAt?.utc(),
                steps = (response?.get("steps") as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().map(::step),
                changes = plans.map(::change),
                answer = response?.let(::answer),
                error = if (payload?.get("kind") == ChatRun.ERROR_KIND) Error(payload.string("code"), payload.string("message")) else null,
            )
        }

        private fun step(step: Map<*, *>) = Step(
            label = step.string("step_label"),
            access = step.string("access"),
            command = step.string("command"),
            output = step.string("output")?.let(::clip),
        )

        private fun answer(response: Map<*, *>): Answer {
            val finding = response["finding"] as? Map<*, *>

            return Answer(
                narration = response.string("narration"),
                finding = finding?.let { Finding(it.string("cause"), it.string("evidence"), it.string("recommendation")) },
                suggestedNextAction = response.string("suggested_next_action"),
            )
        }

        private fun change(plan: ActionPlan): Change {
            val payload = plan.payload
            val decision = plan.decision

            return Change(
                code = plan.code,
                tool = plan.toolName,
                intent = payload.string("intent"),
                args = payload["args"],
                command = (payload["command"] as? List<*>).orEmpty(),
                target = payload["target"],
                risk = plan.risk.value,
                status = plan.status,
                failureReason = plan.failureReason,
                dryRun = (payload[ActionPlan.DRY_RUN_RESULT] as? Map<*, *>)?.let(::clipped),
                decision = decision?.let {
                    Decision(
                        approved = it["approved"] == true,
                        by = it.string("user_id")?.let { id -> runCatching { UUID.fromString(id) }.getOrNull() },
                        at = it.string("at"),
                    )
                },
                result = plan.executionResult?.let(::clipped),
                pullRequest = payload[ActionPlan.PULL_REQUEST],
                appliedAt = plan.appliedAt?.utc(),
            )
        }

        private fun clipped(map: Map<*, *>): Map<String, Any?> =
            map.entries.associate { (key, value) -> key.toString() to if (value is String) clip(value) else value }

        private fun clip(text: String): String {
            if (text.length <= OUTPUT_LIMIT) return text
            val end = if (text[OUTPUT_LIMIT - 1].isHighSurrogate()) OUTPUT_LIMIT - 1 else OUTPUT_LIMIT
            return text.take(end) + "\n…(${text.length - end}자 생략)"
        }

        private fun LocalDateTime.utc(): Instant = toInstant(ZoneOffset.UTC)

        private fun Map<*, *>.string(key: String): String? = this[key] as? String
    }
}
