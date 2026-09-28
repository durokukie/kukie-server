package com.duro.kukie.team.domain

/** 초대의 상태. 처리된 초대도 지우지 않고 상태만 바꾼다. */
enum class InvitationStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    CANCELED,
    EXPIRED,
}
