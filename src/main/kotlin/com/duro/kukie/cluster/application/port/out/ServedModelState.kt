package com.duro.kukie.cluster.application.port.out

import java.time.Instant

/** 쿠버네티스 API 로 읽은 ServedModel. */
data class ServedModelState(
    val model: String?,
    val phase: String?,
    val deleting: Boolean,
    val resourceVersion: String?,
    val keepUntil: Instant?,
    val stopsAt: Instant?,
)
