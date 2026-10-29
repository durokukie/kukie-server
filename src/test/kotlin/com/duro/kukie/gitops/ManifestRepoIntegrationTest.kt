package com.duro.kukie.gitops

import com.duro.kukie.cluster.ClusterFixture
import com.duro.kukie.cluster.domain.Cluster
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.gitops.GitOpsFixture.INSTALLATION_ID
import com.duro.kukie.gitops.GitOpsFixture.REPOSITORY
import com.duro.kukie.gitops.domain.GitHubInstallationRepository
import com.duro.kukie.gitops.domain.ManifestRepoRepository
import com.duro.kukie.gitops.exception.GitOpsErrorCode
import com.duro.kukie.support.FakeGitHubAppGateway
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.support.LoggedInUser
import com.duro.kukie.team.domain.TeamRole
import com.duro.kukie.team.exception.TeamErrorCode
import com.duro.kukie.user.UserFixture
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID

class ManifestRepoIntegrationTest : IntegrationTest() {

    @Autowired
    private lateinit var clusterRepository: ClusterRepository

    @Autowired
    private lateinit var gitHubInstallationRepository: GitHubInstallationRepository

    @Autowired
    private lateinit var manifestRepoRepository: ManifestRepoRepository

    @Autowired
    private lateinit var fakeGitHubAppGateway: FakeGitHubAppGateway

    @Test
    fun `팀 설치가 볼 수 있는 레포를 정규화한 이름으로 클러스터에 연결한다`() {
        // given
        val admin = loggedInUser()
        val cluster = savedCluster(admin)
        installed(cluster, admin)

        // when & then
        connect(cluster, admin, "git@github.com:Acme/Manifests.git").andExpect {
            status { isCreated() }
            jsonPath("$.clusterId") { value(cluster.id.toString()) }
            jsonPath("$.repository") { value(REPOSITORY) }
            jsonPath("$.repositoryId") { value(FakeGitHubAppGateway.repositoryIdOf(REPOSITORY)) }
            jsonPath("$.installationId") { value(INSTALLATION_ID) }
            jsonPath("$.connectedBy") { value(admin.user.id.toString()) }
        }
        manifestRepoRepository.findAllByClusterIdOrderByCreatedAt(cluster.id).map { it.fullName } shouldBe listOf(REPOSITORY)
    }

    @Test
    fun `모양만 다른 같은 레포를 다시 연결하면 기존 연결을 준다`() {
        // given
        val admin = loggedInUser()
        val cluster = savedCluster(admin)
        installed(cluster, admin)
        connect(cluster, admin, "https://github.com/acme/manifests.git").andExpect { status { isCreated() } }

        // when & then
        connect(cluster, admin, "git@github.com:acme/manifests.git").andExpect {
            status { isOk() }
            jsonPath("$.repository") { value(REPOSITORY) }
        }
        manifestRepoRepository.count() shouldBe 1
    }

    @Test
    fun `GitHub 레포 링크가 아니면 연결하지 않는다`() {
        // given
        val admin = loggedInUser()
        val cluster = savedCluster(admin)

        // when & then
        connect(cluster, admin, "https://gitlab.com/acme/manifests").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value(GitOpsErrorCode.INVALID_REPO_URL.code) }
        }
    }

    @Test
    fun `우리 앱이 설치되지 않은 레포는 연결하지 않는다`() {
        // given
        val admin = loggedInUser()
        val cluster = savedCluster(admin)
        installed(cluster, admin)

        // when & then
        connect(cluster, admin, "acme/elsewhere").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value(GitOpsErrorCode.GITHUB_APP_NOT_INSTALLED.code) }
        }
        manifestRepoRepository.count() shouldBe 0
    }

    @Test
    fun `다른 팀에 묶인 설치의 레포는 연결하지 않는다`() {
        // given
        val owner = loggedInUser()
        installed(savedCluster(owner), owner)
        val admin = loggedInUser(UserFixture.user(email = "other@example.com"))
        val cluster = savedCluster(admin)

        // when & then
        connect(cluster, admin, REPOSITORY).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value(GitOpsErrorCode.GITHUB_APP_NOT_INSTALLED.code) }
        }
        fakeGitHubAppGateway.issued shouldBe emptyList()
    }

    @Test
    fun `팀원은 레포를 연결할 수 없다`() {
        // given
        val member = loggedInUser()
        val admin = userRepository.save(UserFixture.user(email = "admin@example.com"))
        val teamId = savedTeam(admin.id to TeamRole.ADMIN, member.user.id to TeamRole.MEMBER)
        val cluster = clusterRepository.save(ClusterFixture.cluster(registeredBy = admin.id, teamId = teamId))

        // when & then
        connect(cluster, member, REPOSITORY).andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(TeamErrorCode.NOT_TEAM_ADMIN.code) }
        }
    }

    @Test
    fun `팀원은 클러스터의 레포 연결 목록을 본다`() {
        // given
        val member = loggedInUser()
        val admin = userRepository.save(UserFixture.user(email = "admin@example.com"))
        val teamId = savedTeam(admin.id to TeamRole.ADMIN, member.user.id to TeamRole.MEMBER)
        val cluster = clusterRepository.save(ClusterFixture.cluster(registeredBy = admin.id, teamId = teamId))
        manifestRepoRepository.save(GitOpsFixture.manifestRepo(teamId, cluster.id, admin.id))

        // when & then
        mockMvc.get("/clusters/${cluster.id}/manifest-repos") {
            authorization(member.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].repository") { value(REPOSITORY) }
        }
    }

    @Test
    fun `연결을 끊으면 지우고 서버가 들고 있던 토큰을 거둔다`() {
        // given
        val admin = loggedInUser()
        val cluster = savedCluster(admin)
        val repo = manifestRepoRepository.save(GitOpsFixture.manifestRepo(cluster.teamId, cluster.id, admin.user.id))

        // when
        mockMvc.delete("/clusters/${cluster.id}/manifest-repos/${repo.id}") {
            authorization(admin.accessToken)
        }.andExpect {
            status { isNoContent() }
        }

        // then
        manifestRepoRepository.count() shouldBe 0
        fakeGitHubAppGateway.revoked shouldBe listOf(FakeGitHubAppGateway.Issued(INSTALLATION_ID, repo.repositoryId))
    }

    @Test
    fun `다른 클러스터가 같은 레포를 쓰면 연결을 끊어도 토큰은 거두지 않는다`() {
        // given
        val admin = loggedInUser()
        val cluster = savedCluster(admin)
        val other = clusterRepository.save(ClusterFixture.cluster(registeredBy = admin.user.id, teamId = cluster.teamId, name = "검증"))
        val repo = manifestRepoRepository.save(GitOpsFixture.manifestRepo(cluster.teamId, cluster.id, admin.user.id))
        manifestRepoRepository.save(GitOpsFixture.manifestRepo(cluster.teamId, other.id, admin.user.id))

        // when
        mockMvc.delete("/clusters/${cluster.id}/manifest-repos/${repo.id}") {
            authorization(admin.accessToken)
        }.andExpect {
            status { isNoContent() }
        }

        // then
        manifestRepoRepository.count() shouldBe 1
        fakeGitHubAppGateway.revoked shouldBe emptyList()
    }

    @Test
    fun `다른 클러스터의 연결은 끊을 수 없다`() {
        // given
        val admin = loggedInUser()
        val cluster = savedCluster(admin)
        val other = clusterRepository.save(ClusterFixture.cluster(registeredBy = admin.user.id, teamId = cluster.teamId, name = "검증"))
        val repo = manifestRepoRepository.save(GitOpsFixture.manifestRepo(cluster.teamId, other.id, admin.user.id))

        // when & then
        mockMvc.delete("/clusters/${cluster.id}/manifest-repos/${repo.id}") {
            authorization(admin.accessToken)
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value(GitOpsErrorCode.MANIFEST_REPO_NOT_CONNECTED.code) }
        }
        manifestRepoRepository.count() shouldBe 1
    }

    @Test
    fun `클러스터를 지우면 레포 연결도 함께 지운다`() {
        // given
        val admin = loggedInUser()
        val cluster = savedCluster(admin)
        manifestRepoRepository.save(GitOpsFixture.manifestRepo(cluster.teamId, cluster.id, admin.user.id))

        // when
        mockMvc.delete("/clusters/${cluster.id}") {
            authorization(admin.accessToken)
        }.andExpect {
            status { isNoContent() }
        }

        // then
        manifestRepoRepository.count() shouldBe 0
    }

    @Test
    fun `팀을 지우면 GitHub App 설치와 레포 연결도 함께 지운다`() {
        // given
        val admin = loggedInUser()
        val cluster = savedCluster(admin)
        installed(cluster, admin)
        manifestRepoRepository.save(GitOpsFixture.manifestRepo(cluster.teamId, cluster.id, admin.user.id))

        // when
        mockMvc.delete("/teams/${cluster.teamId}") {
            authorization(admin.accessToken)
        }.andExpect {
            status { isNoContent() }
        }

        // then
        manifestRepoRepository.count() shouldBe 0
        gitHubInstallationRepository.count() shouldBe 0
    }

    private fun connect(cluster: Cluster, user: LoggedInUser, url: String) =
        mockMvc.post("/clusters/${cluster.id}/manifest-repos") {
            authorization(user.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = mapOf("url" to url).toJson()
        }

    private fun installed(cluster: Cluster, admin: LoggedInUser) {
        fakeGitHubAppGateway.install(INSTALLATION_ID, REPOSITORY)
        gitHubInstallationRepository.save(GitOpsFixture.installation(cluster.teamId, admin.user.id))
    }

    private fun savedCluster(admin: LoggedInUser): Cluster {
        val teamId: UUID = savedTeam(admin.user.id to TeamRole.ADMIN)
        return clusterRepository.save(ClusterFixture.cluster(registeredBy = admin.user.id, teamId = teamId))
    }
}
