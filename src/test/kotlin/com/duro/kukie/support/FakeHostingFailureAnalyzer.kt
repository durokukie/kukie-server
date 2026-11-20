package com.duro.kukie.support

import com.duro.kukie.hosting.application.port.out.HostingFailureAnalyzer

class FakeHostingFailureAnalyzer : HostingFailureAnalyzer, Resettable {

    var answer = DEFAULT_ANSWER
    val prompts = mutableListOf<String>()

    override val enabled: Boolean = true

    var startAnswer = DEFAULT_START_ANSWER

    override fun analyze(system: String, prompt: String, answer: HostingFailureAnalyzer.Answer): String {
        prompts += prompt
        return if (answer == HostingFailureAnalyzer.Answer.START) startAnswer else this.answer
    }

    override fun clear() {
        answer = DEFAULT_ANSWER
        startAnswer = DEFAULT_START_ANSWER
        prompts.clear()
    }

    companion object {
        const val DEFAULT_START_ANSWER = """{"start": "", "confident": false, "reason": "코드 오류라 명령만 바꿔서는 뜨지 않습니다."}"""
        const val DEFAULT_ANSWER = """{"cause": "requirements.txt 에 flask 가 없습니다.", "fixes": ["requirements.txt 에 flask 를 더하세요."]}"""
    }
}
