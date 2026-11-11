package com.duro.kukie.global.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/** 호스팅 배포기가 상태 파일을 적는 S3 버킷과 명령을 꺼내 가는 SQS 큐. */
@ConfigurationProperties(prefix = "hosting")
data class HostingProperties(
    val stateBucket: String? = null,
    val commandsQueueUrl: String? = null,
) {
    val enabled: Boolean
        get() = !stateBucket.isNullOrBlank() && !commandsQueueUrl.isNullOrBlank()
}
