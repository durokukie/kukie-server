package com.duro.kukie.chat.presentation.dto.response

data class RepoLinkStateResponse(
    /** 한 번도 저장하지 않았으면 null. */
    val state: Map<String, Any?>?,
)
