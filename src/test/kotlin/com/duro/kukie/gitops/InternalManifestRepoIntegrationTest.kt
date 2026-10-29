package com.duro.kukie.gitops

import com.duro.kukie.auth.exception.AuthErrorCode
import com.duro.kukie.cluster.ClusterFixture
import com.duro.kukie.cluster.domain.Cluster
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.gitops.GitOpsFixture.INSTALLATION_ID
import com.duro.kukie.gitops.GitOpsFixture.REPOSITORY
import com.duro.kukie.gitops.domain.ManifestRepoRepository
import com.duro.kukie.gitops.domain.RepoName
import com.duro.kukie.gitops.exception.GitOpsErrorCode
import com.duro.kukie.support.FakeGitHubAppGateway
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.team.domain.TeamRole
import com.duro.kukie.user.UserFixture
import com.duro.kukie.user.domain.User
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID

class InternalManifestRepoIntegrationTest : IntegrationTest() {

    @Autowired
    private lateinit var clusterRepository: ClusterRepository

    @Autowired
    private lateinit var manifestRepoRepository: ManifestRepoRepository

    @Autowired
    private lateinit var fakeGitHubAppGateway: FakeGitHubAppGateway

    @Test
    fun `클러스터에 연결된 레포 목록을 준다`() {
        // given
        val cluster = savedCluster()
        manifestRepoRepository.save(GitOpsFixture.manifestRepo(cluster.teamId, cluster.id, cluster.registeredBy))

        // when & then
        mockMvc.get("/internal/clusters/${cluster.id}/manifest-repos") {
            internalToken()
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].repository") { value(REPOSITORY) }
            jsonPath("$[0].installationId") { value(INSTALLATION_ID) }
        }
    }

    @Test
    fun `연결된 레포 하나로 범위를 좁힌 설치 토큰을 준다`() {
        // given
        val cluster = savedCluster()
        fakeGitHubAppGateway.install(INSTALLATION_ID, REPOSITORY, "acme/apps")
        val repo = manifestRepoRepository.save(GitOpsFixture.manifestRepo(cluster.teamId, cluster.id, cluster.registeredBy))

        // when & then
        issueToken(cluster, "https://github.com/Acme/Manifests.git").andExpect {
            status { isOk() }
            jsonPath("$.repository") { value(REPOSITORY) }
            jsonPath("$.repositoryId") { value(repo.repositoryId) }
            jsonPath("$.token") { value(FakeGitHubAppGateway.tokenFor(RepoName.parse(REPOSITORY))) }
            jsonPath("$.expiresAt") { value("2026-10-30T10:00:00Z") }
        }
        fakeGitHubAppGateway.issued shouldBe listOf(FakeGitHubAppGateway.Issued(INSTALLATION_ID, repo.repositoryId))
    }

    @Test
    fun `연결하지 않은 레포의 토큰은 주지 않는다`() {
        // given
        val cluster = savedCluster()
        fakeGitHubAppGateway.install(INSTALLATION_ID, REPOSITORY, "acme/apps")
        manifestRepoRepository.save(GitOpsFixture.manifestRepo(cluster.teamId, cluster.id, cluster.registeredBy))

        // when & then
        issueToken(cluster, "acme/apps").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value(GitOpsErrorCode.MANIFEST_REPO_NOT_CONNECTED.code) }
            jsonPath("$.token") { doesNotExist() }
        }
        fakeGitHubAppGateway.issued shouldBe emptyList()
    }

    @Test
    fun `연결을 끊은 레포의 토큰은 주지 않는다`() {
        // given
        val admin = loggedInUser()
        val cluster = savedCluster(admin.user)
        fakeGitHubAppGateway.install(INSTALLATION_ID, REPOSITORY)
        val repo = manifestRepoRepository.save(GitOpsFixture.manifestRepo(cluster.teamId, cluster.id, cluster.registeredBy))
        issueToken(cluster, REPOSITORY).andExpect { status { isOk() } }
        mockMvc.delete("/clusters/${cluster.id}/manifest-repos/${repo.id}") {
            authorization(admin.accessToken)
        }.andExpect { status { isNoContent() } }

        // when & then
        issueToken(cluster, REPOSITORY).andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value(GitOpsErrorCode.MANIFEST_REPO_NOT_CONNECTED.code) }
            jsonPath("$.token") { doesNotExist() }
        }
        fakeGitHubAppGateway.issued.size shouldBe 1
    }

    @Test
    fun `다른 클러스터에 연결된 레포의 토큰은 주지 않는다`() {
        // given
        val cluster = savedCluster()
        val other = clusterRepository.save(
            ClusterFixture.cluster(registeredBy = cluster.registeredBy, teamId = cluster.teamId, name = "검증"),
        )
        fakeGitHubAppGateway.install(INSTALLATION_ID, REPOSITORY)
        manifestRepoRepository.save(GitOpsFixture.manifestRepo(cluster.teamId, other.id, cluster.registeredBy))

        // when & then
        issueToken(cluster, REPOSITORY).andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value(GitOpsErrorCode.MANIFEST_REPO_NOT_CONNECTED.code) }
        }
    }

    @Test
    fun `내부 토큰이 없으면 설치 토큰을 주지 않는다`() {
        // given
        val cluster = savedCluster()
        manifestRepoRepository.save(GitOpsFixture.manifestRepo(cluster.teamId, cluster.id, cluster.registeredBy))

        // when & then
        mockMvc.post("/internal/clusters/${cluster.id}/manifest-repos/token") {
            contentType = MediaType.APPLICATION_JSON
            content = mapOf("repository" to REPOSITORY).toJson()
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value(AuthErrorCode.INVALID_INTERNAL_TOKEN.code) }
        }
        fakeGitHubAppGateway.issued shouldBe emptyList()
    }

    private fun issueToken(cluster: Cluster, repository: String) =
        mockMvc.post("/internal/clusters/${cluster.id}/manifest-repos/token") {
            internalToken()
            contentType = MediaType.APPLICATION_JSON
            content = mapOf("repository" to repository).toJson()
        }

    private fun savedCluster(admin: User = userRepository.save(UserFixture.user())): Cluster {
        val teamId: UUID = savedTeam(admin.id to TeamRole.ADMIN)
        return clusterRepository.save(ClusterFixture.cluster(registeredBy = admin.id, teamId = teamId))
    }
}
