package com.duro.kukie.support

import com.duro.kukie.hosting.application.port.out.HostingFailureAnalyzer

class FakeHostingFailureAnalyzer : HostingFailureAnalyzer, Resettable {

    var answer = DEFAULT_ANSWER
    val prompts = mutableListOf<String>()

    override val enabled: Boolean = true

    override fun analyze(system: String, prompt: String): String {
        prompts += prompt
        return answer
    }

    override fun clear() {
        answer = DEFAULT_ANSWER
        prompts.clear()
    }

    companion object {
        const val DEFAULT_ANSWER = """{"cause": "requirements.txt 에 flask 가 없습니다.", "fixes": ["requirements.txt 에 flask 를 더하세요."]}"""
    }
}
