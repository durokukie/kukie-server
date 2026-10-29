package com.duro.kukie.gitops.presentation

import com.duro.kukie.gitops.application.HandleGitHubSetupService
import com.duro.kukie.gitops.application.port.`in`.GitHubSetupCommand
import com.duro.kukie.global.config.properties.WebAppProperties
import com.duro.kukie.global.exception.BusinessException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI

/** GitHub App 의 Setup URL. 브라우저가 GitHub 에서 넘어오므로 JSON 대신 늘 웹 앱으로 돌려보낸다. */
@RestController
@RequestMapping("/github-app")
class GitHubSetupController(
    private val handleGitHubSetupService: HandleGitHubSetupService,
    private val webAppProperties: WebAppProperties,
) : GitHubSetupControllerDocs {

    @GetMapping("/setup")
    override fun handleGitHubSetup(
        @RequestParam("installation_id", required = false) installationId: String?,
        @RequestParam("setup_action", required = false) setupAction: String?,
        @RequestParam(required = false) state: String?,
        @RequestParam(required = false) code: String?,
    ): ResponseEntity<Unit> {
        val command = GitHubSetupCommand(installationId?.toLongOrNull(), setupAction, state, code)
        val query = try {
            val result = handleGitHubSetupService(command)
            mapOf(RESULT to result.outcome.value) + listOfNotNull(result.teamId?.let { TEAM_ID to it.toString() })
        } catch (exception: BusinessException) {
            mapOf(RESULT to FAILED, ERROR to exception.errorCode.code)
        }

        return ResponseEntity.status(HttpStatus.FOUND).location(landing(query)).build()
    }

    private fun landing(query: Map<String, String>): URI =
        UriComponentsBuilder.fromUriString(webAppProperties.url)
            .path(LANDING_PATH)
            .apply { query.forEach { (key, value) -> queryParam(key, value) } }
            .build()
            .encode()
            .toUri()

    companion object {
        const val LANDING_PATH = "/github-app/setup"
        private const val RESULT = "result"
        private const val TEAM_ID = "teamId"
        private const val ERROR = "error"
        private const val FAILED = "failed"
    }
}
