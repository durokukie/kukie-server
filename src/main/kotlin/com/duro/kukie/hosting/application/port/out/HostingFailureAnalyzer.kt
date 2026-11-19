package com.duro.kukie.hosting.application.port.out

interface HostingFailureAnalyzer {

    /** false 면 이 서버에 키가 없어 분석을 쓸 수 없다. */
    val enabled: Boolean

    /** 모델의 답을 그대로 준다. 부르지 못했거나 답이 비었으면 HostingAnalysisFailedException. */
    fun analyze(system: String, prompt: String): String
}
