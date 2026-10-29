package com.duro.kukie.gitops.infrastructure

import com.duro.kukie.gitops.application.port.out.GitHubAppGateway
import com.duro.kukie.gitops.application.port.out.GitHubInstallationInfo
import com.duro.kukie.gitops.application.port.out.RepoToken
import com.duro.kukie.gitops.domain.GitHubAccount
import com.duro.kukie.gitops.domain.RepoName
import com.duro.kukie.gitops.exception.GitHubAppNotInstalledException
import com.duro.kukie.gitops.exception.GitHubAppSuspendedException
import com.duro.kukie.gitops.exception.GitHubUnavailableException
import com.duro.kukie.global.config.properties.GitHubAppProperties
import com.duro.kukie.global.util.logger
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.body
import java.net.http.HttpClient
import java.time.Duration
import java.time.Instant

@Component
class RestGitHubAppGateway internal constructor(
    private val restClient: RestClient,
    private val properties: GitHubAppProperties,
    private val credentials: GitHubAppCredentials,
) : GitHubAppGateway {

    @Autowired
    constructor(
        restClientBuilder: RestClient.Builder,
        properties: GitHubAppProperties,
        credentials: GitHubAppCredentials,
    ) : this(
        restClientBuilder.requestFactory(timedRequestFactory()).baseUrl(properties.apiUrl).build(),
        properties,
        credentials,
    )

    private val log = logger()

    override fun findInstallation(installationId: Long): GitHubInstallationInfo? {
        val installation = call {
            notFoundAsNull {
                restClient.get()
                    .uri("/app/installations/{id}", installationId)
                    .headers { it.github(credentials.appJwt()) }
                    .retrieve()
                    .body<InstallationResponse>()
            }
        } ?: return null
        if (installation.appId != properties.id) return null

        return GitHubInstallationInfo(
            installationId = installation.id,
            account = GitHubAccount(login = installation.account.login, type = installation.account.type),
            repositorySelection = installation.repositorySelection,
            suspended = installation.suspendedAt != null,
        )
    }

    override fun installationRepositories(installationId: Long): List<RepoName> {
        val token = createToken(installationId, emptyMap())
        try {
            return generateSequence(1) { it + 1 }
                .take(MAX_PAGES)
                .map { page -> repositoriesPage(token.token, page) }
                .takeWhile { it.isNotEmpty() }
                .flatten()
                .map { RepoName.parse(it.fullName) }
                .toList()
        } finally {
            revoke(token.token)
        }
    }

    override fun findRepoInstallationId(repository: RepoName): Long? {
        val installation = call {
            notFoundAsNull {
                restClient.get()
                    .uri("/repos/{owner}/{repo}/installation", repository.owner, repository.name)
                    .headers { it.github(credentials.appJwt()) }
                    .retrieve()
                    .body<InstallationResponse>()
            }
        } ?: return null

        return installation.id.takeIf { installation.appId == properties.id }
    }

    override fun issueRepoToken(installationId: Long, repository: RepoName): RepoToken {
        credentials.cachedRepoToken(installationId, repository)?.let { return it }

        return repoToken(installationId, createToken(installationId, mapOf("repositories" to listOf(repository.name))))
            .takeIf { it.repository == repository }
            ?: throw GitHubAppNotInstalledException()
    }

    override fun issueRepoToken(installationId: Long, repositoryId: Long): RepoToken {
        credentials.cachedRepoToken(installationId, repositoryId)?.let { return it }

        return repoToken(installationId, createToken(installationId, mapOf("repository_ids" to listOf(repositoryId))))
    }

    override fun revokeRepoToken(installationId: Long, repositoryId: Long) {
        credentials.forget(installationId, repositoryId)?.let { revoke(it.token) }
    }

    private fun repoToken(installationId: Long, response: AccessTokenResponse): RepoToken {
        val repository = response.repositories?.singleOrNull() ?: throw GitHubAppNotInstalledException()
        val token = RepoToken(
            token = response.token,
            expiresAt = response.expiresAt,
            repositoryId = repository.id,
            repository = RepoName.parse(repository.fullName),
        )
        credentials.remember(installationId, token)

        return token
    }

    /** 설치가 없으면(404) 또는 레포를 볼 수 없으면(422) 설치 안 됨, 정지된 설치는 403 이다. */
    private fun createToken(installationId: Long, body: Map<String, Any>): AccessTokenResponse =
        call {
            try {
                restClient.post()
                    .uri("/app/installations/{id}/access_tokens", installationId)
                    .headers { it.github(credentials.appJwt()) }
                    .body(body)
                    .retrieve()
                    .body<AccessTokenResponse>()
            } catch (exception: HttpClientErrorException) {
                when (exception.statusCode) {
                    HttpStatus.NOT_FOUND, HttpStatus.UNPROCESSABLE_CONTENT -> throw GitHubAppNotInstalledException()
                    HttpStatus.FORBIDDEN -> throw GitHubAppSuspendedException()
                    else -> throw exception
                }
            }
        } ?: throw GitHubUnavailableException()

    private fun repositoriesPage(token: String, page: Int): List<RepositoryResponse> =
        call {
            restClient.get()
                .uri("/installation/repositories?per_page={size}&page={page}", PAGE_SIZE, page)
                .headers { it.github(token) }
                .retrieve()
                .body<RepositoriesResponse>()
        }?.repositories.orEmpty()

    private fun revoke(token: String) {
        try {
            restClient.delete()
                .uri("/installation/token")
                .headers { it.github(token) }
                .retrieve()
                .toBodilessEntity()
        } catch (exception: RestClientException) {
            log.warn("GitHub 설치 토큰을 거두지 못했습니다: reason={}", exception.javaClass.simpleName)
        }
    }

    private fun <T> notFoundAsNull(block: () -> T): T? =
        try {
            block()
        } catch (exception: HttpClientErrorException) {
            if (exception.statusCode == HttpStatus.NOT_FOUND) null else throw exception
        }

    /** 응답 본문에는 토큰이 있을 수 있어 예외 메시지 대신 상태만 남긴다. */
    private fun <T> call(block: () -> T): T =
        try {
            block()
        } catch (exception: RestClientException) {
            val status = (exception as? HttpClientErrorException)?.statusCode?.value()
            log.warn("GitHub API 호출에 실패했습니다: status={} reason={}", status, exception.javaClass.simpleName)
            throw GitHubUnavailableException()
        }

    private fun HttpHeaders.github(bearer: String) {
        setBearerAuth(bearer)
        set(HttpHeaders.ACCEPT, "application/vnd.github+json")
        set(API_VERSION_HEADER, API_VERSION)
    }

    private data class InstallationResponse(
        val id: Long,
        @JsonProperty("app_id")
        val appId: Long,
        val account: AccountResponse,
        @JsonProperty("repository_selection")
        val repositorySelection: String,
        @JsonProperty("suspended_at")
        val suspendedAt: String?,
    )

    private data class AccountResponse(
        val login: String,
        val type: String,
    )

    private class AccessTokenResponse(
        val token: String,
        @JsonProperty("expires_at")
        val expiresAt: Instant,
        val repositories: List<RepositoryResponse>?,
    )

    private data class RepositoriesResponse(
        val repositories: List<RepositoryResponse>,
    )

    private data class RepositoryResponse(
        val id: Long,
        @JsonProperty("full_name")
        val fullName: String,
    )

    companion object {
        private const val API_VERSION_HEADER = "X-GitHub-Api-Version"
        private const val API_VERSION = "2022-11-28"
        private const val PAGE_SIZE = 100
        private const val MAX_PAGES = 10
        private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(5)
        private val READ_TIMEOUT: Duration = Duration.ofSeconds(15)

        private fun timedRequestFactory() =
            JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build())
                .apply { setReadTimeout(READ_TIMEOUT) }
    }
}
