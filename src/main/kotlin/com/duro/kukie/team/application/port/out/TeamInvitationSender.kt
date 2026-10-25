package com.duro.kukie.team.application.port.out

/** 초대받은 주소로 초대 메일을 보낸다. 가입 여부에 따라 수락 안내 문구가 다르다. */
interface TeamInvitationSender {
    fun send(email: String, teamName: String, inviterName: String, registered: Boolean)
}
