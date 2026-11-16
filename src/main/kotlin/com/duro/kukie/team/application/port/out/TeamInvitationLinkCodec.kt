package com.duro.kukie.team.application.port.out

import java.util.UUID

/** 초대 메일의 링크에 실어 보내는 초대 id 를 서명하고 확인한다. */
interface TeamInvitationLinkCodec {
    fun encode(invitationId: UUID): String

    /** 서명이 틀리거나 초대 링크용 토큰이 아니면 null. */
    fun decode(token: String): UUID?
}
