package com.duro.kukie.hosting.application

import com.duro.kukie.hosting.domain.HostingDeployment
import com.duro.kukie.hosting.domain.HostingStartRetry

/** 시작 명령을 두고 배포기와 서버가 해 본 일을, 에이전트가 사용자에게 그대로 전할 한국어로 쓴다. */
object HostingStartFlow {

    fun succeeded(first: String?, start: String): String =
        (first?.let { "처음 시작 명령 '$it' 로는 앱이 뜨지 않아, " } ?: "처음 시작 명령으로는 앱이 뜨지 않아, ") +
            "AI 가 고른 '$start' 로 한 번 더 배포해 떴어요. 다음 배포에도 이 명령을 쓰려면 루트의 kukie.json 에 ${kukieJson(start)} 를 넣으세요."

    /**
     * first 는 처음 실패한 배포, again 은 그 재시도 배포(있으면). retry 는 first 에 대해 서버가 한 일이다. 쓸 것이 없으면 null.
     */
    fun failed(first: HostingDeployment, again: HostingDeployment?, retry: HostingStartRetry?): String? {
        val steps = buildList {
            val build = first.build
            when {
                first.config.start != null -> add("kukie.json 의 시작 명령 '${first.config.start}' 로 시작하지 못했어요.")
                build?.corrected != null -> add("알아낸 시작 명령 '${build.corrected}' 를 규칙으로 '${build.start}' 로 고쳐 배포했지만 시작하지 못했어요.")
                build?.start != null -> add("알아낸 시작 명령 '${build.start}' 로 시작하지 못했어요.")
            }
            when {
                again != null -> add("AI 가 고른 '${again.retry?.start}' 로 한 번 더 배포했지만 그것도 시작하지 못했어요.")
                retry != null && retry.status == HostingStartRetry.SKIPPED && retry.reason != null -> add("다시 배포하지 않은 까닭: ${retry.reason}")
            }
        }
        return steps.takeIf { it.isNotEmpty() }?.joinToString(" ")
    }

    fun kukieJson(start: String): String = "{\"start\": \"" + start.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}"
}
