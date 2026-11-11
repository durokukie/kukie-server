package com.duro.kukie.cluster.presentation.dto.response

import com.fasterxml.jackson.annotation.JsonInclude

data class GpuPrecheckStartResponse(
    val name: String,
)

data class GpuPrecheckResponse(
    /** RUNNING, DONE, FAILED, EXPIRED, IMAGE_PULL_FAILED, UNSCHEDULABLE. */
    val state: String,
    val checks: List<GpuPrecheckCheck> = emptyList(),
)

data class GpuPrecheckCheck(
    /** gateway, huggingface, registry. */
    val target: String,
    /** PASS, FAIL. */
    val status: String,
    /** 실패 이유. DNS, CONNECT, TIMEOUT, TLS, OTHER. 통과면 null. */
    @field:JsonInclude(JsonInclude.Include.ALWAYS)
    val reason: String?,
)
