package com.duro.kukie.global.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/** 호스팅 배포기가 상태 파일을 적는 S3 버킷과 명령을 꺼내 가는 SQS 큐. */
@ConfigurationProperties(prefix = "hosting")
data class HostingProperties(
    val stateBucket: String? = null,
    val commandsQueueUrl: String? = null,
    /** 에이전트의 토큰·배포 결과 요청을 붙잡아 두는 최대 시간. 에이전트 셸 명령 시간 제한(Claude Code 기본 2분)보다 짧게 둔다. */
    val agentPollWait: Duration = Duration.ofSeconds(25),
) {
    val enabled: Boolean
        get() = !stateBucket.isNullOrBlank() && !commandsQueueUrl.isNullOrBlank()
}
