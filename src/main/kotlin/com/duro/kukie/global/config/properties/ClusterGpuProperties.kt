package com.duro.kukie.global.config.properties

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Pattern
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.time.Duration

/** 서버가 만드는 ServedModel 의 엔진 설정과 모델 대화의 제한. 이미지는 CRD 규칙대로 digest 로 고정한다. */
@Validated
@ConfigurationProperties(prefix = "cluster.gpu")
data class ClusterGpuProperties(
    @field:Pattern(regexp = ".*@sha256:[a-f0-9]{64}$")
    val engineImage: String =
        "vllm/vllm-openai:v0.32.0@sha256:6ee133356d49ed24cee03dd0cda3d3980279a11c107ce3e752c89e76540f41c9",
    @field:NotEmpty val engineArgs: List<String> = listOf("--max-model-len", "16384", "--gpu-memory-utilization", "0.92"),
    /** 대화 하나의 응답 전체 시간 상한. */
    val chatTimeout: Duration = Duration.ofMinutes(10),
    /** 말이 없는 동안 보내는 keep-alive 간격이자 브라우저가 끊은 것을 알아채는 데 걸리는 최대 시간. CloudFront origin_read_timeout(60초)보다 짧아야 한다. */
    val chatHeartbeat: Duration = Duration.ofSeconds(5),
    /** 서버 한 대가 동시에 옮기는 대화 수. 대화마다 쿠버네티스 연결과 스레드가 하나씩 든다. */
    @field:Min(1) val chatMaxStreams: Int = 32,
)
