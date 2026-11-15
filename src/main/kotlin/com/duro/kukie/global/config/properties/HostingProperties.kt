package com.duro.kukie.global.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/** 호스팅 배포기가 상태 파일을 적는 S3 버킷과 명령을 꺼내 가는 SQS 큐. */
@ConfigurationProperties(prefix = "hosting")
data class HostingProperties(
    /** 호스팅용 GitHub App 의 slug (github.com/apps/<slug>). dev 와 prod 의 App 이 달라 기본값이 없다. 호스팅을 켠 서버에서만 필요하다. */
    val githubApp: String = "",
    val stateBucket: String? = null,
    val commandsQueueUrl: String? = null,
    /** 에이전트의 토큰·배포 결과 요청을 붙잡아 두는 최대 시간. 에이전트 셸 명령 시간 제한(Claude Code 기본 2분)보다 짧게 둔다. */
    val agentPollWait: Duration = Duration.ofSeconds(25),
    /** 에이전트의 배포 결과 요청을 붙잡아 두는 최대 시간. API 앞 CloudFront 의 응답 제한(60초)과 S3 호출(10초)보다 짧게 둔다. */
    val agentDeployWait: Duration = Duration.ofSeconds(40),
) {
    val enabled: Boolean
        get() = !stateBucket.isNullOrBlank() && !commandsQueueUrl.isNullOrBlank()

    init {
        // 다른 환경의 App 을 설치하게 하면 push 가 그 환경의 배포기로 간다.
        require(!enabled || githubApp.isNotBlank()) { "HOSTING_GITHUB_APP must be set when hosting is enabled" }
    }
}
