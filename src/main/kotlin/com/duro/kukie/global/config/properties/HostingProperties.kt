package com.duro.kukie.global.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/** 호스팅 배포기가 상태 파일을 적는 S3 버킷과 명령을 꺼내 가는 SQS 큐. */
@ConfigurationProperties(prefix = "hosting")
data class HostingProperties(
    /** 호스팅용 GitHub App 의 slug (github.com/apps/<slug>). dev 와 prod 의 App 이 다르다. */
    val githubApp: String = "kukie-hosting-dev",
    val stateBucket: String? = null,
    val commandsQueueUrl: String? = null,
) {
    val enabled: Boolean
        get() = !stateBucket.isNullOrBlank() && !commandsQueueUrl.isNullOrBlank()
}
