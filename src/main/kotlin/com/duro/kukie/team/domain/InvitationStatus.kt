package com.duro.kukie.team.domain

/**
 * 초대의 상태. 초대를 보냈다고 바로 멤버가 되지는 않으며 받는 사람이 Accept 해야 한다 (제품기획서 02 §4).
 * 수락·거절·취소된 초대도 기록으로 남기므로 지우지 않고 상태만 바꾼다.
 */
enum class InvitationStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    CANCELED,
    ;

    val isPending: Boolean get() = this == PENDING
}
