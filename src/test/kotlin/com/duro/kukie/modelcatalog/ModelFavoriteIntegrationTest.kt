package com.duro.kukie.modelcatalog

import com.duro.kukie.cluster.ClusterFixture
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.cluster.exception.ClusterErrorCode
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.team.domain.TeamRole
import com.duro.kukie.team.exception.TeamErrorCode
import com.duro.kukie.user.UserFixture
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.put
import java.util.UUID

class ModelFavoriteIntegrationTest : IntegrationTest() {

    @Autowired
    private lateinit var clusterRepository: ClusterRepository

    @Test
    fun `팀 Admin 은 즐겨찾기를 넣고 빼고, 같은 모델을 두 번 넣어도 하나다`() {
        // given
        val (clusterId, token) = cluster(TeamRole.ADMIN)

        // when & then
        put(clusterId, token, "Qwen/Qwen3-32B").andExpect {
            status { isOk() }
            jsonPath("$.models.length()") { value(1) }
            jsonPath("$.models[0]") { value("Qwen/Qwen3-32B") }
        }
        put(clusterId, token, "openai/gpt-oss-20b")
        put(clusterId, token, "Qwen/Qwen3-32B").andExpect {
            status { isOk() }
            jsonPath("$.models.length()") { value(2) }
        }
        delete(clusterId, token, "Qwen/Qwen3-32B").andExpect {
            status { isOk() }
            jsonPath("$.models.length()") { value(1) }
            jsonPath("$.models[0]") { value("openai/gpt-oss-20b") }
        }
        delete(clusterId, token, "Qwen/Qwen3-32B").andExpect {
            status { isOk() }
            jsonPath("$.models.length()") { value(1) }
        }
    }

    @Test
    fun `즐겨찾기는 클러스터마다 따로다`() {
        // given
        val (first, token) = cluster(TeamRole.ADMIN)
        val owner = clusterRepository.findById(first).orElseThrow()
        val second = clusterRepository.save(
            ClusterFixture.cluster(registeredBy = owner.registeredBy, teamId = owner.teamId, name = "second"),
        ).id
        put(first, token, "Qwen/Qwen3-32B")

        // when & then
        get(second, token).andExpect {
            status { isOk() }
            jsonPath("$.models.length()") { value(0) }
        }
    }

    @Test
    fun `팀 구성원은 즐겨찾기를 볼 수 있지만 바꿀 수 없다`() {
        // given
        val (clusterId, token) = cluster(TeamRole.MEMBER)

        // when & then
        get(clusterId, token).andExpect { status { isOk() } }
        put(clusterId, token, "Qwen/Qwen3-32B").andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(TeamErrorCode.NOT_TEAM_ADMIN.code) }
        }
        delete(clusterId, token, "Qwen/Qwen3-32B").andExpect { status { isForbidden() } }
    }

    @Test
    fun `팀 구성원이 아니면 클러스터를 찾지 못한다`() {
        // given
        val (clusterId, _) = cluster(TeamRole.ADMIN)
        val outsider = loggedInUser(UserFixture.user(email = "outsider@example.com"))

        // when & then
        get(clusterId, outsider.accessToken).andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value(ClusterErrorCode.NOT_FOUND.code) }
        }
    }

    @Test
    fun `허깅페이스 조직·이름 모양이 아니면 넣지 않는다`() {
        // given
        val (clusterId, token) = cluster(TeamRole.ADMIN)

        // when & then
        put(clusterId, token, "no-slash").andExpect { status { isBadRequest() } }
        get(clusterId, token).andExpect { jsonPath("$.models.length()") { value(0) } }
    }

    private fun cluster(role: TeamRole): Pair<UUID, String> {
        val user = loggedInUser()
        val teamId = savedTeam(user.user.id to role)
        val cluster = clusterRepository.save(ClusterFixture.cluster(registeredBy = user.user.id, teamId = teamId))
        return cluster.id to user.accessToken
    }

    private fun get(clusterId: UUID, token: String): ResultActionsDsl =
        mockMvc.get("/clusters/$clusterId/gpu/model-favorites") { authorization(token) }

    private fun put(clusterId: UUID, token: String, model: String): ResultActionsDsl =
        mockMvc.put("/clusters/$clusterId/gpu/model-favorites") {
            authorization(token)
            contentType = MediaType.APPLICATION_JSON
            content = """{"model":"$model"}"""
        }

    private fun delete(clusterId: UUID, token: String, model: String): ResultActionsDsl =
        mockMvc.delete("/clusters/$clusterId/gpu/model-favorites") {
            authorization(token)
            contentType = MediaType.APPLICATION_JSON
            content = """{"model":"$model"}"""
        }
}
