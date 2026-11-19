package com.duro.kukie.hosting.application

import com.duro.kukie.hosting.application.port.out.HostingAgentRateLimit
import com.duro.kukie.hosting.application.port.out.HostingAnalysisCache
import com.duro.kukie.hosting.application.port.out.HostingFailureAnalyzer
import com.duro.kukie.hosting.exception.HostingAnalysisDisabledException
import com.duro.kukie.hosting.exception.HostingAnalysisNotAvailableException
import com.duro.kukie.hosting.exception.HostingDeploymentNotFoundException
import com.duro.kukie.hosting.exception.HostingTooManyAnalysesException
import com.duro.kukie.hosting.presentation.dto.response.HostingFailureAnalysisResponse
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode
import java.time.Duration
import java.util.UUID

@Service
class AnalyzeHostingFailureService(
    private val hostingApps: HostingApps,
    private val hostingFailureAnalyzer: HostingFailureAnalyzer,
    private val hostingAnalysisCache: HostingAnalysisCache,
    private val hostingAgentRateLimit: HostingAgentRateLimit,
    private val objectMapper: ObjectMapper,
) {

    operator fun invoke(userId: UUID, app: String, deploymentId: String): HostingFailureAnalysisResponse {
        if (!hostingFailureAnalyzer.enabled) throw HostingAnalysisDisabledException()
        val owned = hostingApps.requireOwned(userId, app)
        val deployment = hostingApps.deployment(owned, deploymentId) ?: throw HostingDeploymentNotFoundException()
        if (deployment.phase != FAILED || deployment.failure?.code !in ANALYZABLE) throw HostingAnalysisNotAvailableException()

        val cacheKey = "${owned.team}:${owned.app}:${owned.generation}:${deployment.deploymentId}"
        hostingAnalysisCache.get(cacheKey)?.let { return parse(it).copy(cached = true) }
        if (!hostingAgentRateLimit.tryAcquire("analysis:$userId", DAILY_LIMIT, Duration.ofDays(1))) throw HostingTooManyAnalysesException()

        val log = hostingApps.buildLogTail(owned, deployment.deploymentId, LOG_TAIL_BYTES).orEmpty()
        val answer = hostingFailureAnalyzer.analyze(HostingFailureAnalysisPrompt.SYSTEM, HostingFailureAnalysisPrompt.of(deployment, log))
        val analysis = parse(answer)
        hostingAnalysisCache.put(cacheKey, objectMapper.writeValueAsString(analysis))
        return analysis
    }

    /** 모델이 JSON 밖에 글을 붙이거나 JSON 을 깨뜨려도, 답을 버리지 않고 원인으로 보여 준다. */
    private fun parse(answer: String): HostingFailureAnalysisResponse {
        val start = answer.indexOf('{')
        val end = answer.lastIndexOf('}')
        val node = if (start >= 0 && end > start) runCatching { objectMapper.readTree(answer.substring(start, end + 1)) as? ObjectNode }.getOrNull() else null
        val cause = node?.get("cause")?.takeIf { it.isString }?.asString()?.trim()
        if (node == null || cause.isNullOrBlank()) return HostingFailureAnalysisResponse(answer.trim().take(MAX_CAUSE), emptyList())
        val fixes = node.get("fixes")?.takeIf { it.isArray }?.mapNotNull { fix -> fix.takeIf { it.isString }?.asString()?.trim()?.takeIf(String::isNotEmpty) }.orEmpty()
        return HostingFailureAnalysisResponse(cause.take(MAX_CAUSE), fixes.take(MAX_FIXES))
    }

    companion object {
        private const val FAILED = "failed"

        // 나머지 실패는 규칙이 원인과 고칠 방법을 이미 알려 주거나 Kukie 쪽 문제다
        private val ANALYZABLE = setOf("build_failed", "build_timeout", "start_failed")
        private const val DAILY_LIMIT = 10
        private const val LOG_TAIL_BYTES = 12_000
        private const val MAX_CAUSE = 2_000
        private const val MAX_FIXES = 5
    }
}
