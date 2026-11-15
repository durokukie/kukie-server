package com.duro.kukie.hosting.domain

import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * 코딩 에이전트의 배포 한 번. id 는 무작위 소문자 hex 40자로, 배포기가 커밋 sha 자리에 쓴다(앞 12자가 이미지 태그, 앞 7자가 배포 id).
 * commandId 는 시작한 뒤에, deploymentId·generation 은 배포기가 그 명령으로 배포를 시작한 것을 본 뒤에 생긴다.
 */
data class HostingAgentDeploy(
    val id: String,
    val userId: UUID,
    val githubId: Long,
    val login: String,
    val team: String,
    val app: String,
    val bytes: Long,
    val createdAt: Instant,
    val commandId: String? = null,
    val startedAt: Instant? = null,
    val deploymentId: String? = null,
    val generation: String? = null,
) {
    val key: String
        get() = "uploads/$team/$app/$id.tar.gz"
}

interface HostingAgentDeployRepository {

    fun save(deploy: HostingAgentDeploy, ttl: Duration)

    fun find(id: String): HostingAgentDeploy?

    /** 계정에서 시작했고 아직 끝나지 않은 배포. */
    fun activeOf(team: String): String?

    /** 계정의 진행 중 배포 자리를 잡는다. 비어 있거나 이미 id 의 자리면 true. */
    fun claimActive(team: String, id: String, ttl: Duration): Boolean

    /** 자리가 id 의 것일 때만 비운다. */
    fun clearActive(team: String, id: String)

    /** 배포의 명령을 정한다. 먼저 정한 쪽의 commandId 를 준다(같은 배포의 시작이 겹쳐도 명령은 하나). */
    fun claimStart(id: String, commandId: String, ttl: Duration): String

    fun releaseStart(id: String)
}
