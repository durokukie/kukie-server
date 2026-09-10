package com.duro.kukie.team.application.port.out

/** 아직 가입하지 않은 사람에게 초대 메일을 보낸다 (제품기획서 02 §4). 가입한 사람은 Inbox 로 받는다. */
interface TeamInvitationSender {
    fun send(email: String, teamName: String, inviterName: String)
}
