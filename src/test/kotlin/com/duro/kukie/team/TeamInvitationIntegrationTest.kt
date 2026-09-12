package com.duro.kukie.team

import com.duro.kukie.notification.domain.NotificationKind
import com.duro.kukie.notification.domain.NotificationRepository
import com.duro.kukie.notification.exception.NotificationErrorCode
import com.duro.kukie.support.FakeTeamInvitationSender
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.support.LoggedInUser
import com.duro.kukie.team.domain.*
import com.duro.kukie.team.exception.TeamErrorCode
import com.duro.kukie.team.presentation.dto.request.InviteTeamMemberRequest
import com.duro.kukie.team.presentation.dto.request.UpdateTeamMemberRoleRequest
import com.duro.kukie.user.UserFixture
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*

class TeamInvitationIntegrationTest : IntegrationTest() {

    @Autowired
    private lateinit var teamRepository: TeamRepository

    @Autowired
    private lateinit var teamMembershipRepository: TeamMembershipRepository

    @Autowired
    private lateinit var teamInvitationRepository: TeamInvitationRepository

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @Autowired
    private lateinit var invitationSender: FakeTeamInvitationSender

    // ── 초대 보내기 ────────────────────────────────────────────

    @Test
    fun `관리자가 초대하면 수락을 기다리는 초대가 생긴다`() {
        // given
        val admin = adminOfNewTeam()

        // when
        mockMvc.post("/teams/${admin.teamId}/invitations") {
            authorization(admin.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = InviteTeamMemberRequest(INVITEE_EMAIL).toJson()
        }.andExpect {
            // then — 초대를 보냈다고 바로 멤버가 되지는 않는다 (제품기획서 02 §4)
            status { isCreated() }
            jsonPath("$.email") { value(INVITEE_EMAIL) }
            jsonPath("$.status") { value(InvitationStatus.PENDING.name) }
        }

        teamMembershipRepository.findAllByTeamId(admin.teamId).size shouldBe 1
    }

    @Test
    fun `초대는 7일 뒤에 만료된다`() {
        // given
        val admin = adminOfNewTeam()

        // when
        invite(admin, INVITEE_EMAIL)

        // then
        val invitation = teamInvitationRepository.findAll().first()
        val expectedExpiresAt = invitation.createdAt.plus(TeamInvitation.VALIDITY)
        ChronoUnit.SECONDS.between(invitation.expiresAt, expectedExpiresAt) shouldBe 0
    }

    @Test
    fun `만료된 주소는 다시 초대할 수 있다`() {
        // given — 기한이 지난 대기 초대는 자리를 비켜 준다
        val admin = adminOfNewTeam()
        val expired = savedInvitation(admin.teamId, INVITEE_EMAIL, admin.userId, expiresAt = yesterday())

        // when
        mockMvc.post("/teams/${admin.teamId}/invitations") {
            authorization(admin.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = InviteTeamMemberRequest(INVITEE_EMAIL).toJson()
        }.andExpect { status { isCreated() } }

        // then
        teamInvitationRepository.findByIdOrThrow(expired.id).status shouldBe InvitationStatus.EXPIRED
        teamInvitationRepository.findAll().count { it.status == InvitationStatus.PENDING } shouldBe 1
    }

    @Test
    fun `가입하지 않은 주소를 초대하면 초대 메일을 보낸다`() {
        // given
        val admin = adminOfNewTeam()

        // when
        invite(admin, INVITEE_EMAIL)

        // then
        val sent = invitationSender.sentTo(INVITEE_EMAIL)
        sent.size shouldBe 1
        sent.first().teamName shouldBe TeamFixture.DEFAULT_NAME
        sent.first().inviterName shouldBe UserFixture.DEFAULT_NAME
    }

    @Test
    fun `이미 가입한 사람에게는 메일을 보내지 않는다`() {
        // given — 가입한 사람은 받은 초대함으로 받는다 (제품기획서 02 §4)
        val admin = adminOfNewTeam()
        loggedInUser(UserFixture.user(email = INVITEE_EMAIL))

        // when
        invite(admin, INVITEE_EMAIL)

        // then
        invitationSender.sentCount() shouldBe 0
        teamInvitationRepository.findAll().size shouldBe 1
    }

    @Test
    fun `메일 발송이 실패해도 초대는 남는다`() {
        // given — 메일은 커밋 뒤에 최선 노력으로 나간다. SMTP 가 잠깐 죽었다고 관리자가 초대를
        // 아예 못 하게 되면 안 된다.
        val admin = adminOfNewTeam()
        invitationSender.shouldFail = true

        // when
        mockMvc.post("/teams/${admin.teamId}/invitations") {
            authorization(admin.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = InviteTeamMemberRequest(INVITEE_EMAIL).toJson()
        }.andExpect { status { isCreated() } }

        // then
        teamInvitationRepository.findAll().size shouldBe 1
    }

    @Test
    fun `초대가 실패하면 메일도 나가지 않는다`() {
        // given — 메일을 트랜잭션 안에서 보내면 커밋이 실패했을 때 없는 초대의 메일이 나간다.
        val admin = adminOfNewTeam()
        invite(admin, INVITEE_EMAIL)
        val before = invitationSender.sentCount()

        // when — 같은 주소를 다시 초대해 실패시킨다
        mockMvc.post("/teams/${admin.teamId}/invitations") {
            authorization(admin.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = InviteTeamMemberRequest(INVITEE_EMAIL).toJson()
        }.andExpect { status { isConflict() } }

        // then
        invitationSender.sentCount() shouldBe before
    }

    @Test
    fun `대소문자만 다른 주소로 초대해도 자기 초대를 본다`() {
        // given — 초대는 주소 문자열로 사람을 가리킨다. 대소문자가 어긋나면 그 초대는 초대함에도
        // 안 뜨고 수락도 재초대도 막혀 영영 남는다 (자동 리뷰 지적).
        val admin = adminOfNewTeam()
        invite(admin, INVITEE_EMAIL.uppercase())
        val invitee = loggedInUser(UserFixture.user(email = INVITEE_EMAIL))

        // when & then — 초대함에 뜨고
        mockMvc.get("/inbox") {
            authorization(invitee.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.invitations.length()") { value(1) }
        }

        // 수락도 된다
        val invitation = teamInvitationRepository.findAll().first()
        mockMvc.post("/inbox/invitations/${invitation.id}/accept") {
            authorization(invitee.accessToken)
        }.andExpect { status { isNoContent() } }

        teamMembershipRepository.existsByTeamIdAndUserId(admin.teamId, invitee.user.id) shouldBe true
    }

    @Test
    fun `대소문자만 다른 주소는 같은 주소로 본다`() {
        // given
        val admin = adminOfNewTeam()
        invite(admin, INVITEE_EMAIL)

        // when & then
        mockMvc.post("/teams/${admin.teamId}/invitations") {
            authorization(admin.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = InviteTeamMemberRequest(INVITEE_EMAIL.uppercase()).toJson()
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value(TeamErrorCode.INVITATION_ALREADY_SENT.code) }
        }
    }

    @Test
    fun `이미 팀에 있는 사람은 대소문자가 달라도 초대할 수 없다`() {
        // given
        val admin = adminOfNewTeam()
        val member = loggedInUser(UserFixture.user(email = INVITEE_EMAIL))
        teamMembershipRepository.save(TeamFixture.membership(admin.teamId, member.user.id, TeamRole.MEMBER))

        // when & then
        mockMvc.post("/teams/${admin.teamId}/invitations") {
            authorization(admin.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = InviteTeamMemberRequest(INVITEE_EMAIL.uppercase()).toJson()
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value(TeamErrorCode.ALREADY_TEAM_MEMBER.code) }
        }
    }

    @Test
    fun `구성원은 초대할 수 없다`() {
        // given
        val team = teamRepository.save(TeamFixture.team())
        val member = loggedInUser()
        teamMembershipRepository.save(TeamFixture.membership(team.id, member.user.id, TeamRole.MEMBER))

        // when & then
        mockMvc.post("/teams/${team.id}/invitations") {
            authorization(member.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = InviteTeamMemberRequest(INVITEE_EMAIL).toJson()
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(TeamErrorCode.NOT_TEAM_ADMIN.code) }
        }
    }

    @Test
    fun `이미 팀에 있는 사람은 초대할 수 없다`() {
        // given
        val admin = adminOfNewTeam()
        val member = loggedInUser(UserFixture.user(email = INVITEE_EMAIL))
        teamMembershipRepository.save(TeamFixture.membership(admin.teamId, member.user.id, TeamRole.MEMBER))

        // when & then
        mockMvc.post("/teams/${admin.teamId}/invitations") {
            authorization(admin.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = InviteTeamMemberRequest(INVITEE_EMAIL).toJson()
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value(TeamErrorCode.ALREADY_TEAM_MEMBER.code) }
        }
    }

    @Test
    fun `같은 주소를 두 번 초대할 수 없다`() {
        // given
        val admin = adminOfNewTeam()
        invite(admin, INVITEE_EMAIL)

        // when & then
        mockMvc.post("/teams/${admin.teamId}/invitations") {
            authorization(admin.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = InviteTeamMemberRequest(INVITEE_EMAIL).toJson()
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value(TeamErrorCode.INVITATION_ALREADY_SENT.code) }
        }
    }

    @Test
    fun `이메일 형식이 아니면 초대할 수 없다`() {
        val admin = adminOfNewTeam()

        mockMvc.post("/teams/${admin.teamId}/invitations") {
            authorization(admin.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = InviteTeamMemberRequest("주소아님").toJson()
        }.andExpect { status { isBadRequest() } }
    }

    // ── 초대 취소 ──────────────────────────────────────────────

    @Test
    fun `관리자는 보낸 초대를 취소한다`() {
        // given
        val admin = adminOfNewTeam()
        val invitee = loggedInUser(UserFixture.user(email = INVITEE_EMAIL))
        val invitation = savedInvitation(admin.teamId, INVITEE_EMAIL, admin.userId)

        // when
        mockMvc.delete("/teams/${admin.teamId}/invitations/${invitation.id}") {
            authorization(admin.accessToken)
        }.andExpect { status { isNoContent() } }

        // then — 기록은 남되 받는 사람의 초대함에서는 사라진다
        teamInvitationRepository.findByIdOrThrow(invitation.id).status shouldBe InvitationStatus.CANCELED
        mockMvc.get("/inbox") {
            authorization(invitee.accessToken)
        }.andExpect { jsonPath("$.invitations.length()") { value(0) } }
    }

    @Test
    fun `취소한 초대는 수락할 수 없다`() {
        // given
        val admin = adminOfNewTeam()
        val invitee = loggedInUser(UserFixture.user(email = INVITEE_EMAIL))
        val invitation = savedInvitation(admin.teamId, INVITEE_EMAIL, admin.userId)
        mockMvc.delete("/teams/${admin.teamId}/invitations/${invitation.id}") {
            authorization(admin.accessToken)
        }.andExpect { status { isNoContent() } }

        // when & then
        mockMvc.post("/inbox/invitations/${invitation.id}/accept") {
            authorization(invitee.accessToken)
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value(TeamErrorCode.INVITATION_NOT_PENDING.code) }
        }
    }

    @Test
    fun `취소한 주소를 다시 초대할 수 있다`() {
        // given
        val admin = adminOfNewTeam()
        val invitation = savedInvitation(admin.teamId, INVITEE_EMAIL, admin.userId)
        mockMvc.delete("/teams/${admin.teamId}/invitations/${invitation.id}") {
            authorization(admin.accessToken)
        }.andExpect { status { isNoContent() } }

        // when & then
        mockMvc.post("/teams/${admin.teamId}/invitations") {
            authorization(admin.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = InviteTeamMemberRequest(INVITEE_EMAIL).toJson()
        }.andExpect { status { isCreated() } }
    }

    @Test
    fun `구성원은 초대를 취소할 수 없다`() {
        // given
        val admin = adminOfNewTeam()
        val member = loggedInUser(UserFixture.user(email = "member@example.com"))
        teamMembershipRepository.save(TeamFixture.membership(admin.teamId, member.user.id, TeamRole.MEMBER))
        val invitation = savedInvitation(admin.teamId, INVITEE_EMAIL, admin.userId)

        // when & then
        mockMvc.delete("/teams/${admin.teamId}/invitations/${invitation.id}") {
            authorization(member.accessToken)
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(TeamErrorCode.NOT_TEAM_ADMIN.code) }
        }
    }

    @Test
    fun `다른 팀의 초대는 취소할 수 없다`() {
        // given — 남의 팀에 어떤 초대가 있는지 응답 코드로 알려 주지 않는다
        val admin = adminOfNewTeam()
        val otherAdmin = adminOfNewTeam(email = "other-admin@example.com")
        val invitation = savedInvitation(otherAdmin.teamId, INVITEE_EMAIL, otherAdmin.userId)

        // when & then
        mockMvc.delete("/teams/${admin.teamId}/invitations/${invitation.id}") {
            authorization(admin.accessToken)
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value(TeamErrorCode.INVITATION_NOT_FOUND.code) }
        }
        teamInvitationRepository.findByIdOrThrow(invitation.id).status shouldBe InvitationStatus.PENDING
    }

    @Test
    fun `이미 처리된 초대는 취소할 수 없다`() {
        // given
        val admin = adminOfNewTeam()
        val invitee = loggedInUser(UserFixture.user(email = INVITEE_EMAIL))
        val invitation = savedInvitation(admin.teamId, INVITEE_EMAIL, admin.userId)
        mockMvc.post("/inbox/invitations/${invitation.id}/accept") {
            authorization(invitee.accessToken)
        }.andExpect { status { isNoContent() } }

        // when & then
        mockMvc.delete("/teams/${admin.teamId}/invitations/${invitation.id}") {
            authorization(admin.accessToken)
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value(TeamErrorCode.INVITATION_NOT_PENDING.code) }
        }
    }

    @Test
    fun `없는 초대는 취소할 수 없다`() {
        val admin = adminOfNewTeam()

        mockMvc.delete("/teams/${admin.teamId}/invitations/${UUID.randomUUID()}") {
            authorization(admin.accessToken)
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value(TeamErrorCode.INVITATION_NOT_FOUND.code) }
        }
    }

    // ── 받은 초대함 ────────────────────────────────────────────

    @Test
    fun `내 주소로 온 초대를 받은 초대함에서 본다`() {
        // given
        val admin = adminOfNewTeam()
        val invitee = loggedInUser(UserFixture.user(email = INVITEE_EMAIL))
        invite(admin, INVITEE_EMAIL)

        // when & then
        mockMvc.get("/inbox") {
            authorization(invitee.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.invitations.length()") { value(1) }
            jsonPath("$.invitations[0].team.name") { value(TeamFixture.DEFAULT_NAME) }
            jsonPath("$.invitations[0].invitedBy") { value(UserFixture.DEFAULT_NAME) }
        }
    }

    @Test
    fun `초대받은 뒤에 가입해도 자기 초대를 본다`() {
        // given — 초대는 회원 id가 아니라 이메일 주소로 저장된다 (제품기획서 02 §4 미가입자 흐름)
        val admin = adminOfNewTeam()
        invite(admin, INVITEE_EMAIL)

        // when — 초대가 만들어진 다음에 그 주소로 가입한다
        val invitee = loggedInUser(UserFixture.user(email = INVITEE_EMAIL))

        // then
        mockMvc.get("/inbox") {
            authorization(invitee.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.invitations.length()") { value(1) }
        }
    }

    @Test
    fun `남의 주소로 온 초대는 보이지 않는다`() {
        // given
        val admin = adminOfNewTeam()
        val other = loggedInUser(UserFixture.user(email = "other@example.com"))
        invite(admin, INVITEE_EMAIL)

        // when & then
        mockMvc.get("/inbox") {
            authorization(other.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.invitations.length()") { value(0) }
        }
    }

    @Test
    fun `만료된 초대는 받은 초대함에 보이지 않는다`() {
        // given
        val admin = adminOfNewTeam()
        val invitee = loggedInUser(UserFixture.user(email = INVITEE_EMAIL))
        savedInvitation(admin.teamId, INVITEE_EMAIL, admin.userId, expiresAt = yesterday())

        // when & then
        mockMvc.get("/inbox") {
            authorization(invitee.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.invitations.length()") { value(0) }
        }
    }

    // ── 초대 수락 ──────────────────────────────────────────────

    @Test
    fun `초대를 수락하면 구성원이 된다`() {
        // given
        val admin = adminOfNewTeam()
        val invitee = loggedInUser(UserFixture.user(email = INVITEE_EMAIL))
        val invitation = savedInvitation(admin.teamId, INVITEE_EMAIL, admin.userId)

        // when
        mockMvc.post("/inbox/invitations/${invitation.id}/accept") {
            authorization(invitee.accessToken)
        }.andExpect { status { isNoContent() } }

        // then — 새 구성원의 역할은 MEMBER 다
        val membership = teamMembershipRepository.findByTeamIdAndUserId(admin.teamId, invitee.user.id)
        membership?.role shouldBe TeamRole.MEMBER
        teamInvitationRepository.findAll().first().status shouldBe InvitationStatus.ACCEPTED
    }

    @Test
    fun `수락한 초대는 받은 초대함에서 사라진다`() {
        // given
        val admin = adminOfNewTeam()
        val invitee = loggedInUser(UserFixture.user(email = INVITEE_EMAIL))
        val invitation = savedInvitation(admin.teamId, INVITEE_EMAIL, admin.userId)

        // when
        mockMvc.post("/inbox/invitations/${invitation.id}/accept") {
            authorization(invitee.accessToken)
        }.andExpect { status { isNoContent() } }

        // then
        mockMvc.get("/inbox") {
            authorization(invitee.accessToken)
        }.andExpect { jsonPath("$.invitations.length()") { value(0) } }
    }

    @Test
    fun `남에게 온 초대는 수락할 수 없다`() {
        // given
        val admin = adminOfNewTeam()
        val other = loggedInUser(UserFixture.user(email = "other@example.com"))
        val invitation = savedInvitation(admin.teamId, INVITEE_EMAIL, admin.userId)

        // when & then
        mockMvc.post("/inbox/invitations/${invitation.id}/accept") {
            authorization(other.accessToken)
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(TeamErrorCode.NOT_MY_INVITATION.code) }
        }
    }

    @Test
    fun `이미 처리한 초대는 다시 수락할 수 없다`() {
        // given
        val admin = adminOfNewTeam()
        val invitee = loggedInUser(UserFixture.user(email = INVITEE_EMAIL))
        val invitation = savedInvitation(admin.teamId, INVITEE_EMAIL, admin.userId)
        mockMvc.post("/inbox/invitations/${invitation.id}/decline") {
            authorization(invitee.accessToken)
        }.andExpect { status { isNoContent() } }

        // when & then
        mockMvc.post("/inbox/invitations/${invitation.id}/accept") {
            authorization(invitee.accessToken)
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value(TeamErrorCode.INVITATION_NOT_PENDING.code) }
        }
    }

    @Test
    fun `만료된 초대는 수락할 수 없다`() {
        // given
        val admin = adminOfNewTeam()
        val invitee = loggedInUser(UserFixture.user(email = INVITEE_EMAIL))
        val invitation = savedInvitation(admin.teamId, INVITEE_EMAIL, admin.userId, expiresAt = yesterday())

        // when & then
        mockMvc.post("/inbox/invitations/${invitation.id}/accept") {
            authorization(invitee.accessToken)
        }.andExpect {
            status { isGone() }
            jsonPath("$.code") { value(TeamErrorCode.INVITATION_EXPIRED.code) }
        }
        teamMembershipRepository.existsByTeamIdAndUserId(admin.teamId, invitee.user.id) shouldBe false
    }

    @Test
    fun `없는 초대는 수락할 수 없다`() {
        val invitee = loggedInUser(UserFixture.user(email = INVITEE_EMAIL))

        mockMvc.post("/inbox/invitations/${UUID.randomUUID()}/accept") {
            authorization(invitee.accessToken)
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value(TeamErrorCode.INVITATION_NOT_FOUND.code) }
        }
    }

    // ── 초대 거절 ──────────────────────────────────────────────

    @Test
    fun `초대를 거절하면 구성원이 되지 않는다`() {
        // given
        val admin = adminOfNewTeam()
        val invitee = loggedInUser(UserFixture.user(email = INVITEE_EMAIL))
        val invitation = savedInvitation(admin.teamId, INVITEE_EMAIL, admin.userId)

        // when
        mockMvc.post("/inbox/invitations/${invitation.id}/decline") {
            authorization(invitee.accessToken)
        }.andExpect { status { isNoContent() } }

        // then
        teamMembershipRepository.existsByTeamIdAndUserId(admin.teamId, invitee.user.id) shouldBe false
        teamInvitationRepository.findAll().first().status shouldBe InvitationStatus.DECLINED
    }

    @Test
    fun `거절한 주소를 다시 초대할 수 있다`() {
        // given
        val admin = adminOfNewTeam()
        val invitee = loggedInUser(UserFixture.user(email = INVITEE_EMAIL))
        val invitation = savedInvitation(admin.teamId, INVITEE_EMAIL, admin.userId)
        mockMvc.post("/inbox/invitations/${invitation.id}/decline") {
            authorization(invitee.accessToken)
        }.andExpect { status { isNoContent() } }

        // when & then — 거절 기록은 남지만 대기 중인 초대는 없으므로 다시 보낼 수 있다
        mockMvc.post("/teams/${admin.teamId}/invitations") {
            authorization(admin.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = InviteTeamMemberRequest(INVITEE_EMAIL).toJson()
        }.andExpect { status { isCreated() } }
    }

    // ── 역할 변경 알림 ─────────────────────────────────────────

    @Test
    fun `역할이 바뀌면 당사자에게 알림이 남는다`() {
        // given — 역할 변경에는 상대방 승인이 없으므로 알림이 유일한 통지다 (제품기획서 02 §3)
        val admin = adminOfNewTeam()
        val member = loggedInUser(UserFixture.user(email = INVITEE_EMAIL))
        teamMembershipRepository.save(TeamFixture.membership(admin.teamId, member.user.id, TeamRole.MEMBER))

        // when
        mockMvc.patch("/teams/${admin.teamId}/members/${member.user.id}") {
            authorization(admin.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = UpdateTeamMemberRoleRequest(TeamRole.ADMIN).toJson()
        }.andExpect { status { isNoContent() } }

        // then
        val notifications = notificationRepository.findAllByUserIdOrderByCreatedAtDesc(member.user.id)
        notifications.size shouldBe 1
        notifications.first().kind shouldBe NotificationKind.TEAM_ROLE_CHANGED
        notifications.first().role shouldBe TeamRole.ADMIN
        notifications.first().teamName shouldBe TeamFixture.DEFAULT_NAME
    }

    @Test
    fun `역할 변경 알림을 받은 초대함에서 본다`() {
        // given
        val admin = adminOfNewTeam()
        val member = loggedInUser(UserFixture.user(email = INVITEE_EMAIL))
        teamMembershipRepository.save(TeamFixture.membership(admin.teamId, member.user.id, TeamRole.MEMBER))
        mockMvc.patch("/teams/${admin.teamId}/members/${member.user.id}") {
            authorization(admin.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = UpdateTeamMemberRoleRequest(TeamRole.ADMIN).toJson()
        }.andExpect { status { isNoContent() } }

        // when & then
        mockMvc.get("/inbox") {
            authorization(member.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.notifications.length()") { value(1) }
            jsonPath("$.notifications[0].kind") { value(NotificationKind.TEAM_ROLE_CHANGED.name) }
            jsonPath("$.notifications[0].payload.role") { value(TeamRole.ADMIN.name) }
            jsonPath("$.notifications[0].payload.teamName") { value(TeamFixture.DEFAULT_NAME) }
            jsonPath("$.notifications[0].readAt") { value(null) }
        }
    }

    @Test
    fun `역할이 그대로면 알림을 남기지 않는다`() {
        // given
        val admin = adminOfNewTeam()
        val member = loggedInUser(UserFixture.user(email = INVITEE_EMAIL))
        teamMembershipRepository.save(TeamFixture.membership(admin.teamId, member.user.id, TeamRole.MEMBER))

        // when
        mockMvc.patch("/teams/${admin.teamId}/members/${member.user.id}") {
            authorization(admin.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = UpdateTeamMemberRoleRequest(TeamRole.MEMBER).toJson()
        }.andExpect { status { isNoContent() } }

        // then
        notificationRepository.findAllByUserIdOrderByCreatedAtDesc(member.user.id).isEmpty() shouldBe true
    }

    // ── 알림 읽음 처리 ─────────────────────────────────────────

    @Test
    fun `알림을 읽으면 읽은 시각이 남는다`() {
        // given
        val member = memberWhoseRoleChanged()
        val notification = notificationRepository.findAllByUserIdOrderByCreatedAtDesc(member.user.id).first()

        // when
        mockMvc.post("/inbox/notifications/${notification.id}/read") {
            authorization(member.accessToken)
        }.andExpect { status { isNoContent() } }

        // then
        notificationRepository.findAllByUserIdOrderByCreatedAtDesc(member.user.id).first().readAt shouldNotBe null
    }

    @Test
    fun `이미 읽은 알림을 다시 읽어도 처음 읽은 시각을 유지한다`() {
        // given
        val member = memberWhoseRoleChanged()
        val notification = notificationRepository.findAllByUserIdOrderByCreatedAtDesc(member.user.id).first()
        mockMvc.post("/inbox/notifications/${notification.id}/read") {
            authorization(member.accessToken)
        }.andExpect { status { isNoContent() } }
        val firstReadAt = notificationRepository.findAllByUserIdOrderByCreatedAtDesc(member.user.id).first().readAt

        // when
        mockMvc.post("/inbox/notifications/${notification.id}/read") {
            authorization(member.accessToken)
        }.andExpect { status { isNoContent() } }

        // then
        notificationRepository.findAllByUserIdOrderByCreatedAtDesc(member.user.id).first().readAt shouldBe firstReadAt
    }

    @Test
    fun `남의 알림은 읽을 수 없다`() {
        // given
        val member = memberWhoseRoleChanged()
        val other = loggedInUser(UserFixture.user(email = "other@example.com"))
        val notification = notificationRepository.findAllByUserIdOrderByCreatedAtDesc(member.user.id).first()

        // when & then
        mockMvc.post("/inbox/notifications/${notification.id}/read") {
            authorization(other.accessToken)
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(NotificationErrorCode.NOT_MY_NOTIFICATION.code) }
        }
    }

    @Test
    fun `없는 알림은 읽을 수 없다`() {
        val me = loggedInUser()

        mockMvc.post("/inbox/notifications/${UUID.randomUUID()}/read") {
            authorization(me.accessToken)
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value(NotificationErrorCode.NOTIFICATION_NOT_FOUND.code) }
        }
    }

    // ── 팀 삭제 ────────────────────────────────────────────────

    @Test
    fun `팀을 지우면 대기 중인 초대도 사라진다`() {
        // given
        val admin = adminOfNewTeam()
        invite(admin, INVITEE_EMAIL)

        // when
        mockMvc.delete("/teams/${admin.teamId}") {
            authorization(admin.accessToken)
        }.andExpect { status { isNoContent() } }

        // then
        teamInvitationRepository.findAll().isEmpty() shouldBe true
    }

    // ── 도우미 ─────────────────────────────────────────────────

    private fun adminOfNewTeam(email: String = UserFixture.DEFAULT_EMAIL): TeamAdmin {
        val admin = loggedInUser(UserFixture.user(email = email))
        val team = teamRepository.save(TeamFixture.team())
        teamMembershipRepository.save(TeamFixture.membership(team.id, admin.user.id))

        return TeamAdmin(teamId = team.id, userId = admin.user.id, accessToken = admin.accessToken)
    }

    /** 역할이 바뀌어 알림을 하나 받은 구성원. */
    private fun memberWhoseRoleChanged(): LoggedInUser {
        val admin = adminOfNewTeam()
        val member = loggedInUser(UserFixture.user(email = INVITEE_EMAIL))
        teamMembershipRepository.save(TeamFixture.membership(admin.teamId, member.user.id, TeamRole.MEMBER))
        mockMvc.patch("/teams/${admin.teamId}/members/${member.user.id}") {
            authorization(admin.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = UpdateTeamMemberRoleRequest(TeamRole.ADMIN).toJson()
        }.andExpect { status { isNoContent() } }

        return member
    }

    private fun invite(admin: TeamAdmin, email: String) {
        mockMvc.post("/teams/${admin.teamId}/invitations") {
            authorization(admin.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = InviteTeamMemberRequest(email).toJson()
        }.andExpect { status { isCreated() } }
    }

    private fun savedInvitation(
        teamId: UUID,
        email: String,
        invitedBy: UUID,
        expiresAt: LocalDateTime = LocalDateTime.now().plus(TeamInvitation.VALIDITY),
    ): TeamInvitation = teamInvitationRepository.save(TeamFixture.invitation(teamId, email, invitedBy, expiresAt))

    private fun yesterday(): LocalDateTime = LocalDateTime.now().minusDays(1)

    private data class TeamAdmin(
        val teamId: UUID,
        val userId: UUID,
        val accessToken: String,
    )

    companion object {
        private const val INVITEE_EMAIL = "invitee@example.com"
    }
}
