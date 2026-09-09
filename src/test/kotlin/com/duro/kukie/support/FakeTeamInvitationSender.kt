package com.duro.kukie.support

import com.duro.kukie.team.application.port.out.TeamInvitationSender

class FakeTeamInvitationSender : TeamInvitationSender, Resettable {

    private val sent = mutableListOf<SentInvitation>()

    override fun send(email: String, teamName: String, inviterName: String) {
        sent += SentInvitation(email = email, teamName = teamName, inviterName = inviterName)
    }

    fun sentTo(email: String): List<SentInvitation> = sent.filter { it.email == email }

    fun sentCount(): Int = sent.size

    override fun clear() = sent.clear()

    data class SentInvitation(
        val email: String,
        val teamName: String,
        val inviterName: String,
    )
}
