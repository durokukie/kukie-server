package com.duro.kukie.global.mail

import org.springframework.stereotype.Component
import org.thymeleaf.ITemplateEngine
import org.thymeleaf.context.Context
import java.util.Locale

/**
 * 메일 본문을 템플릿에서 만든다. 본문을 Kotlin 문자열로 조립하면 값마다 이스케이프를 **기억해야** 하고,
 * 한 번 잊으면 사용자가 지은 팀 이름이 그대로 마크업이 된다 (자동 리뷰 지적, 실제 재현됨).
 *
 * Thymeleaf 의 인라인 출력은 기본이 이스케이프다 — 날것으로 내보내는 문법을 일부러 쓰지 않는 한
 * 안전한 쪽이 기본값이 된다.
 */
@Component
class MailTemplate(
    private val templateEngine: ITemplateEngine,
) {

    fun render(name: String, values: Map<String, Any>): String =
        templateEngine.process(name, Context(Locale.KOREA, values))
}
