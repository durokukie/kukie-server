package com.duro.kukie.hosting.infrastructure

import com.duro.kukie.global.config.properties.OAuthProperties
import com.duro.kukie.global.util.logger
import com.duro.kukie.hosting.application.port.out.GitHubAccountAge
import com.duro.kukie.hosting.exception.HostingUnavailableException
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.body
import java.time.Instant

/**
 * 공개 프로필을 숫자 id 로 읽는다(계정명은 바뀐다). 회원의 토큰은 연결할 때만 받고 저장하지 않아서 쓸 수 없다.
 * OAuth 앱의 client id·secret 을 붙이면 같은 주소에서 나가는 다른 요청과 시간당 60회 한도를 나눠 쓰지 않는다.
 */
@Component
class RestGitHubAccountAge(
    private val restClient: RestClient,
    private val oAuthProperties: OAuthProperties,
) : GitHubAccountAge {

    private val log = logger()

    override fun createdAt(githubId: Long): Instant =
        try {
            restClient.get()
                .uri(USER_URL, githubId)
                .headers { it.setBasicAuth(oAuthProperties.github.clientId, oAuthProperties.github.clientSecret) }
                .retrieve()
                .body<GitHubUserResponse>()
                ?.createdAt
        } catch (exception: RestClientException) {
            log.warn("GitHub 계정을 읽지 못했습니다: id={}, cause={}", githubId, exception.message)
            null
        } ?: throw HostingUnavailableException()

    private data class GitHubUserResponse(
        @JsonProperty("created_at")
        val createdAt: Instant?,
    )

    companion object {
        private const val USER_URL = "https://api.github.com/user/{id}"
    }
}
