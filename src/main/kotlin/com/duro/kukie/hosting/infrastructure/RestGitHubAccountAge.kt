package com.duro.kukie.hosting.infrastructure

import com.duro.kukie.global.config.properties.OAuthProperties
import com.duro.kukie.global.util.logger
import com.duro.kukie.hosting.application.port.out.GitHubAccountAge
import com.duro.kukie.hosting.application.port.out.GitHubAccountNow
import com.duro.kukie.hosting.exception.HostingUnavailableException
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.body
import java.net.http.HttpClient
import java.time.Duration
import java.time.Instant

/**
 * 공개 프로필을 숫자 id 로 읽는다(계정명은 바뀐다). 회원의 토큰은 연결할 때만 받고 저장하지 않아서 쓸 수 없다.
 * OAuth 앱의 client id·secret 을 붙이면 같은 주소에서 나가는 다른 요청과 시간당 60회 한도를 나눠 쓰지 않는다.
 */
@Component
class RestGitHubAccountAge internal constructor(
    private val restClient: RestClient,
    private val oAuthProperties: OAuthProperties,
) : GitHubAccountAge {

    // GitHub 이 답을 끌어도 신청 요청이 서버 스레드를 붙잡고 있지 않게 시간 제한을 둔다
    @Autowired
    constructor(restClientBuilder: RestClient.Builder, oAuthProperties: OAuthProperties) : this(
        restClientBuilder
            .requestFactory(JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(TIMEOUT).build()).apply { setReadTimeout(TIMEOUT) })
            .build(),
        oAuthProperties,
    )

    private val log = logger()

    override fun read(githubId: Long): GitHubAccountNow =
        try {
            restClient.get()
                .uri(USER_URL, githubId)
                .headers { it.setBasicAuth(oAuthProperties.github.clientId, oAuthProperties.github.clientSecret) }
                .retrieve()
                .body<GitHubUserResponse>()
                ?.let { GitHubAccountNow(it.login ?: return@let null, it.createdAt ?: return@let null) }
        } catch (exception: RestClientException) {
            log.warn("GitHub 계정을 읽지 못했습니다: id={}, cause={}", githubId, exception.message)
            null
        } ?: throw HostingUnavailableException()

    private data class GitHubUserResponse(
        val login: String?,
        @JsonProperty("created_at")
        val createdAt: Instant?,
    )

    companion object {
        private val TIMEOUT: Duration = Duration.ofSeconds(5)
        private const val USER_URL = "https://api.github.com/user/{id}"
    }
}
