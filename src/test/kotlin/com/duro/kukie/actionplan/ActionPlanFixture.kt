package com.duro.kukie.actionplan

import com.duro.kukie.actionplan.domain.ActionPlan
import com.duro.kukie.actionplan.domain.DryRunStatus
import com.duro.kukie.actionplan.domain.PlanCall
import com.duro.kukie.actionplan.domain.PlanRisk
import java.util.UUID

object ActionPlanFixture {

    const val TOOL = "scale_resource"
    const val INTENT = "web 디플로이먼트를 3개로 늘린다"
    val ARGS: Map<String, Any?> = linkedMapOf("kind" to "deployment", "name" to "web", "replicas" to 3)
    val COMMAND: List<Any?> = listOf("kubectl", "scale", "deployment/web", "--replicas=3", "-n", "web")
    val TARGET: Map<String, Any?> = linkedMapOf(
        "context" to "prod",
        "namespace" to "web",
        "resources" to listOf(linkedMapOf("kind" to "deployment", "name" to "web")),
    )

    fun payload(intent: String = INTENT): Map<String, Any?> = linkedMapOf(
        "created_at" to "2026-10-14T09:00:00.123456+00:00",
        "args" to ARGS,
        "skill" to "실습",
        "target" to TARGET,
        "command" to COMMAND,
        "intent" to intent,
        "expected_effects" to listOf("파드가 3개가 된다"),
        "side_effects" to listOf("잠깐 부하가 늘 수 있다"),
    )

    fun plan(
        runId: UUID,
        toolCallId: String = "call-1",
        code: String = "ap-261014-0900-$TOOL-$toolCallId",
        toolName: String = TOOL,
        risk: PlanRisk = PlanRisk.CAUTION,
        payload: Map<String, Any?> = payload(),
    ): ActionPlan = ActionPlan(
        code = code,
        runId = runId,
        toolCallId = toolCallId,
        toolName = toolName,
        risk = risk,
        payload = payload,
        requestHash = "h".repeat(64),
    )

    fun call(
        tool: String = TOOL,
        args: Map<String, Any?> = ARGS,
        command: List<Any?> = COMMAND,
        risk: PlanRisk = PlanRisk.CAUTION,
        target: Map<String, Any?> = TARGET,
    ): PlanCall = PlanCall(tool = tool, args = args, command = command, risk = risk, target = target)

    fun ActionPlan.offered(): ActionPlan = apply {
        recordDryRun(DryRunStatus.SUCCEEDED, "deployment.apps/web scaled (server dry run)", "")
        offer("되돌리려면 replicas 를 1 로 줄이면 된다")
    }

    fun ActionPlan.approved(userId: UUID): ActionPlan = offered().apply { approve(call(), userId) }

    fun ActionPlan.executing(userId: UUID): ActionPlan = approved(userId).apply { start() }
}
