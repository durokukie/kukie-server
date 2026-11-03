package com.duro.kukie.global.config.properties

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Pattern
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/** 서버가 만드는 ServedModel 의 엔진 설정. 이미지는 CRD 규칙대로 digest 로 고정한다. */
@Validated
@ConfigurationProperties(prefix = "cluster.gpu")
data class ClusterGpuProperties(
    @field:Pattern(regexp = ".*@sha256:[a-f0-9]{64}$")
    val engineImage: String =
        "vllm/vllm-openai:v0.32.0@sha256:6ee133356d49ed24cee03dd0cda3d3980279a11c107ce3e752c89e76540f41c9",
    @field:NotEmpty val engineArgs: List<String> = listOf("--max-model-len", "16384", "--gpu-memory-utilization", "0.92"),
)
