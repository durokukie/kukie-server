package com.duro.kukie.support

import com.duro.kukie.team.application.port.out.TeamInvitationSender

class FakeTeamInvitationSender : TeamInvitationSender, Resettable {

    private val sent = mutableListOf<SentInvitation>()

    /** SMTP 가 죽은 상황을 흉내낸다. */
    var shouldFail = false

    override fun send(email: String, teamName: String, inviterName: String) {
        if (shouldFail) throw IllegalStateException("메일 서버에 연결할 수 없다")
        sent += SentInvitation(email = email, teamName = teamName, inviterName = inviterName)
    }

    fun sentTo(email: String): List<SentInvitation> = sent.filter { it.email == email }

    fun sentCount(): Int = sent.size

    override fun clear() {
        sent.clear()
        shouldFail = false
    }

    data class SentInvitation(
        val email: String,
        val teamName: String,
        val inviterName: String,
    )
}
