package com.duro.kukie.actionplan.infrastructure

import com.duro.kukie.actionplan.application.port.out.ActionPlanMarkdownRenderer
import com.duro.kukie.actionplan.domain.ActionPlan
import org.springframework.stereotype.Component
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Component
class SnakeYamlActionPlanMarkdownRenderer : ActionPlanMarkdownRenderer {

    private val yaml = Yaml(
        DumperOptions().apply {
            defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
            isAllowUnicode = true
        },
    )

    override fun render(plan: ActionPlan): String {
        val details = yaml.dump(metadata(plan)).trimEnd()
        return "# Action Plan\n\n```yaml\n$details\n```\n\n${body(plan)}"
    }

    private fun metadata(plan: ActionPlan): Map<String, Any?> {
        val payload = plan.payload
        return linkedMapOf(
            "id" to plan.code,
            "created_at" to (payload["created_at"] ?: plan.createdAt.wire()),
            "call_id" to plan.toolCallId,
            "tool" to plan.toolName,
            "args" to (payload["args"] ?: emptyMap<String, Any?>()),
            "skill" to (payload["skill"] ?: ""),
            "target" to (payload["target"] ?: emptyMap<String, Any?>()),
            "command" to (payload["command"] ?: emptyList<Any?>()),
            "risk_level" to plan.risk.value,
            "status" to plan.status.name,
            "dry_run_result" to payload[ActionPlan.DRY_RUN_RESULT],
            "decision_guidance" to payload[ActionPlan.DECISION_GUIDANCE],
            "decision" to plan.decision,
            "execution_result" to plan.executionResult,
            "failure_reason" to plan.failureReason?.name,
            "applied_at" to plan.appliedAt?.wire(),
            "run_id" to plan.runId.toString(),
        )
    }

    private fun body(plan: ActionPlan): String {
        val payload = plan.payload
        val intent = payload["intent"] as? String ?: ""

        return "# Intent\n\n$intent\n\n" +
            "## Expected Effects\n\n${bullets(payload["expected_effects"])}\n\n" +
            "## Side Effects\n\n${bullets(payload["side_effects"])}\n"
    }

    private fun bullets(items: Any?): String =
        (items as? List<*>).orEmpty().joinToString("\n") { "- $it" }

    private fun LocalDateTime.wire(): String = DateTimeFormatter.ISO_INSTANT.format(toInstant(ZoneOffset.UTC))
}
