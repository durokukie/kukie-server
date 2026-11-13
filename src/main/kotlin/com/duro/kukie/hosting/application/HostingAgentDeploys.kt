package com.duro.kukie.hosting.application

import com.duro.kukie.hosting.domain.HostingAgentDeploy
import com.duro.kukie.hosting.domain.HostingAgentDeployRepository
import com.duro.kukie.hosting.exception.HostingDeployNotFoundException
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID

@Component
class HostingAgentDeploys(
    private val hostingAgentDeployRepository: HostingAgentDeployRepository,
) {

    /** 남의 배포는 없는 것과 같다. id 를 추측해도 남의 결과나 로그를 볼 수 없다. */
    fun requireOwned(userId: UUID, id: String): HostingAgentDeploy =
        id.takeIf { ID.matches(it) }?.let(hostingAgentDeployRepository::find)?.takeIf { it.userId == userId }
            ?: throw HostingDeployNotFoundException()

    fun save(deploy: HostingAgentDeploy) = hostingAgentDeployRepository.save(deploy, RECORD_TTL)

    /** 계정의 진행 중 배포. 다른 회원의 것(GitHub 계정을 옮긴 경우)은 이 회원이 끝낼 수 없으니 없는 것으로 본다. */
    fun activeOf(userId: UUID, team: String): String? =
        hostingAgentDeployRepository.activeOf(team)?.takeIf { hostingAgentDeployRepository.find(it)?.userId == userId }

    companion object {
        /** 업로드가 지워지는 때(수명 규칙 7일)와 같다. */
        val RECORD_TTL: Duration = Duration.ofDays(7)
        private val ID = Regex("[0-9a-f]{40}")

        /** 배포기의 앱 이름 규칙(appName(x) == x): 영소문자로 시작, 영소문자·숫자·하이픈, 하이픈 연속 금지, 30자 이하. */
        private val APP_NAME = Regex("[a-z][a-z0-9]*(-[a-z0-9]+)*")
        private const val MAX_APP = 30
        private const val MAX_TEAM_APP = 50

        fun validName(team: String, app: String): Boolean =
            APP_NAME.matches(app) && app.length <= MAX_APP && team.length + app.length <= MAX_TEAM_APP

        /** 폴더 이름을 앱 이름으로: 배포기가 레포 이름에서 앱 이름을 만드는 방법에, 팀 이름과 합친 길이 제한을 더했다. 못 만들면 null. */
        fun suggest(name: String, team: String): String? {
            var app = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
            if (app.firstOrNull()?.isDigit() == true) app = "app-$app"
            if (app.isEmpty()) app = "app"
            return app.take(minOf(MAX_APP, MAX_TEAM_APP - team.length)).trimEnd('-').takeIf { validName(team, it) }
        }
    }
}
