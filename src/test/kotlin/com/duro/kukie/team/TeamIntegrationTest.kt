package com.duro.kukie.team

import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.team.domain.TeamMembershipRepository
import com.duro.kukie.team.domain.TeamRepository
import com.duro.kukie.team.domain.TeamRole
import com.duro.kukie.team.exception.TeamErrorCode
import com.duro.kukie.team.presentation.dto.request.CreateTeamRequest
import com.duro.kukie.team.presentation.dto.request.UpdateTeamMemberRoleRequest
import com.duro.kukie.team.presentation.dto.request.UpdateTeamRequest
import com.duro.kukie.user.UserFixture
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.util.UUID

class TeamIntegrationTest : IntegrationTest() {

    @Autowired
    private lateinit var teamRepository: TeamRepository

    @Autowired
    private lateinit var teamMembershipRepository: TeamMembershipRepository

    // ── 팀 생성 ────────────────────────────────────────────────

    @Test
    fun `팀을 만들면 생성자가 관리자가 된다`() {
        // given
        val me = loggedInUser()
        val request = CreateTeamRequest(TeamFixture.DEFAULT_NAME)

        // when & then
        mockMvc.post("/teams") {
            authorization(me.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = request.toJson()
        }.andExpect {
            status { isCreated() }
            jsonPath("$.name") { value(TeamFixture.DEFAULT_NAME) }
            jsonPath("$.role") { value(TeamRole.ADMIN.name) }
        }

        val memberships = teamMembershipRepository.findAllByUserId(me.user.id)
        memberships.size shouldBe 1
        memberships.first().role shouldBe TeamRole.ADMIN
    }

    @Test
    fun `로그인하지 않으면 팀을 만들 수 없다`() {
        mockMvc.post("/teams") {
            contentType = MediaType.APPLICATION_JSON
            content = CreateTeamRequest(TeamFixture.DEFAULT_NAME).toJson()
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `팀 이름이 비어 있으면 만들 수 없다`() {
        val me = loggedInUser()

        mockMvc.post("/teams") {
            authorization(me.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = CreateTeamRequest(" ").toJson()
        }.andExpect { status { isBadRequest() } }
    }

    // ── 내 팀 목록 ─────────────────────────────────────────────

    @Test
    fun `내가 속한 팀과 역할을 조회한다`() {
        // given
        val me = loggedInUser()
        val other = loggedInUser(UserFixture.user(email = "other@example.com"))
        val myTeam = teamRepository.save(TeamFixture.team("내 팀"))
        val otherTeam = teamRepository.save(TeamFixture.team("남의 팀"))
        teamMembershipRepository.save(TeamFixture.membership(myTeam.id, me.user.id, TeamRole.MEMBER))
        teamMembershipRepository.save(TeamFixture.membership(otherTeam.id, other.user.id))

        // when & then
        mockMvc.get("/teams") {
            authorization(me.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].name") { value("내 팀") }
            jsonPath("$[0].role") { value(TeamRole.MEMBER.name) }
        }
    }

    // ── 구성원 조회 ────────────────────────────────────────────

    @Test
    fun `팀 구성원 목록을 조회한다`() {
        // given
        val admin = loggedInUser()
        val member = loggedInUser(UserFixture.user(name = "구성원", email = "member@example.com"))
        val team = teamRepository.save(TeamFixture.team())
        teamMembershipRepository.save(TeamFixture.membership(team.id, admin.user.id))
        teamMembershipRepository.save(TeamFixture.membership(team.id, member.user.id, TeamRole.MEMBER))

        // when & then
        mockMvc.get("/teams/${team.id}/members") {
            authorization(member.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
        }
    }

    @Test
    fun `팀 구성원이 아니면 구성원 목록을 조회할 수 없다`() {
        // given
        val stranger = loggedInUser()
        val team = teamRepository.save(TeamFixture.team())

        // when & then
        mockMvc.get("/teams/${team.id}/members") {
            authorization(stranger.accessToken)
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(TeamErrorCode.NOT_TEAM_MEMBER.code) }
        }
    }

    // ── 팀 정보 수정 ───────────────────────────────────────────

    @Test
    fun `관리자는 팀 이름을 변경한다`() {
        // given
        val admin = loggedInUser()
        val team = teamRepository.save(TeamFixture.team())
        teamMembershipRepository.save(TeamFixture.membership(team.id, admin.user.id))

        // when & then
        mockMvc.patch("/teams/${team.id}") {
            authorization(admin.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = UpdateTeamRequest("새 이름").toJson()
        }.andExpect {
            status { isOk() }
            jsonPath("$.name") { value("새 이름") }
        }
    }

    @Test
    fun `구성원은 팀 이름을 변경할 수 없다`() {
        // given
        val member = loggedInUser()
        val team = teamRepository.save(TeamFixture.team())
        teamMembershipRepository.save(TeamFixture.membership(team.id, member.user.id, TeamRole.MEMBER))

        // when & then
        mockMvc.patch("/teams/${team.id}") {
            authorization(member.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = UpdateTeamRequest("새 이름").toJson()
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(TeamErrorCode.NOT_TEAM_ADMIN.code) }
        }
    }

    // ── 역할 변경 ──────────────────────────────────────────────

    @Test
    fun `관리자는 구성원을 관리자로 올린다`() {
        // given
        val admin = loggedInUser()
        val member = loggedInUser(UserFixture.user(email = "member@example.com"))
        val team = teamRepository.save(TeamFixture.team())
        teamMembershipRepository.save(TeamFixture.membership(team.id, admin.user.id))
        val membership = teamMembershipRepository.save(
            TeamFixture.membership(team.id, member.user.id, TeamRole.MEMBER),
        )

        // when
        mockMvc.patch("/teams/${team.id}/members/${member.user.id}") {
            authorization(admin.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = UpdateTeamMemberRoleRequest(TeamRole.ADMIN).toJson()
        }.andExpect { status { isNoContent() } }

        // then
        teamMembershipRepository.findById(membership.id).get().role shouldBe TeamRole.ADMIN
    }

    @Test
    fun `마지막 관리자는 구성원으로 내릴 수 없다`() {
        // given
        val admin = loggedInUser()
        val team = teamRepository.save(TeamFixture.team())
        teamMembershipRepository.save(TeamFixture.membership(team.id, admin.user.id))

        // when & then
        mockMvc.patch("/teams/${team.id}/members/${admin.user.id}") {
            authorization(admin.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = UpdateTeamMemberRoleRequest(TeamRole.MEMBER).toJson()
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value(TeamErrorCode.ADMIN_REQUIRED.code) }
        }
    }

    @Test
    fun `관리자가 둘이면 한 명은 구성원으로 내릴 수 있다`() {
        // given
        val admin = loggedInUser()
        val another = loggedInUser(UserFixture.user(email = "another@example.com"))
        val team = teamRepository.save(TeamFixture.team())
        teamMembershipRepository.save(TeamFixture.membership(team.id, admin.user.id))
        teamMembershipRepository.save(TeamFixture.membership(team.id, another.user.id))

        // when & then
        mockMvc.patch("/teams/${team.id}/members/${another.user.id}") {
            authorization(admin.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = UpdateTeamMemberRoleRequest(TeamRole.MEMBER).toJson()
        }.andExpect { status { isNoContent() } }
    }

    @Test
    fun `팀에 없는 사용자의 역할은 변경할 수 없다`() {
        // given
        val admin = loggedInUser()
        val team = teamRepository.save(TeamFixture.team())
        teamMembershipRepository.save(TeamFixture.membership(team.id, admin.user.id))

        // when & then
        mockMvc.patch("/teams/${team.id}/members/${UUID.randomUUID()}") {
            authorization(admin.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = UpdateTeamMemberRoleRequest(TeamRole.ADMIN).toJson()
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(TeamErrorCode.NOT_TEAM_MEMBER.code) }
        }
    }

    // ── 구성원 제거 ────────────────────────────────────────────

    @Test
    fun `관리자는 구성원을 제거한다`() {
        // given
        val admin = loggedInUser()
        val member = loggedInUser(UserFixture.user(email = "member@example.com"))
        val team = teamRepository.save(TeamFixture.team())
        teamMembershipRepository.save(TeamFixture.membership(team.id, admin.user.id))
        teamMembershipRepository.save(TeamFixture.membership(team.id, member.user.id, TeamRole.MEMBER))

        // when
        mockMvc.delete("/teams/${team.id}/members/${member.user.id}") {
            authorization(admin.accessToken)
        }.andExpect { status { isNoContent() } }

        // then
        teamMembershipRepository.existsByTeamIdAndUserId(team.id, member.user.id) shouldBe false
    }

    @Test
    fun `마지막 관리자는 제거할 수 없다`() {
        // given
        val admin = loggedInUser()
        val team = teamRepository.save(TeamFixture.team())
        teamMembershipRepository.save(TeamFixture.membership(team.id, admin.user.id))

        // when & then
        mockMvc.delete("/teams/${team.id}/members/${admin.user.id}") {
            authorization(admin.accessToken)
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value(TeamErrorCode.ADMIN_REQUIRED.code) }
        }
    }

    // ── 팀 삭제 ────────────────────────────────────────────────

    @Test
    fun `관리자는 팀을 삭제한다`() {
        // given
        val admin = loggedInUser()
        val team = teamRepository.save(TeamFixture.team())
        teamMembershipRepository.save(TeamFixture.membership(team.id, admin.user.id))

        // when
        mockMvc.delete("/teams/${team.id}") {
            authorization(admin.accessToken)
        }.andExpect { status { isNoContent() } }

        // then
        teamRepository.existsById(team.id) shouldBe false
        teamMembershipRepository.findAllByTeamId(team.id).isEmpty() shouldBe true
    }

    @Test
    fun `구성원은 팀을 삭제할 수 없다`() {
        // given
        val member = loggedInUser()
        val team = teamRepository.save(TeamFixture.team())
        teamMembershipRepository.save(TeamFixture.membership(team.id, member.user.id, TeamRole.MEMBER))

        // when & then
        mockMvc.delete("/teams/${team.id}") {
            authorization(member.accessToken)
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(TeamErrorCode.NOT_TEAM_ADMIN.code) }
        }
    }
}
