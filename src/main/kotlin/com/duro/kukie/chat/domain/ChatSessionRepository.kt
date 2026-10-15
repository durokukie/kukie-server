package com.duro.kukie.chat.domain

import com.duro.kukie.chat.exception.ChatNotFoundException
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.findByIdOrNull
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ChatSessionRepository : JpaRepository<ChatSession, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ChatSession s where s.id = :id")
    fun lockById(@Param("id") id: UUID): ChatSession?

    /** ChatPermission 의 읽기 규칙과 같아야 한다. */
    @Query(
        """
        select s from ChatSession s join Cluster c on c.id = s.clusterId
        where ((s.userId = :userId and s.visibility = com.duro.kukie.chat.domain.ChatVisibility.PRIVATE)
            or (s.visibility = com.duro.kukie.chat.domain.ChatVisibility.SHARED
                and c.teamId in (select m.teamId from TeamMembership m where m.userId = :userId)))
          and (:clusterId is null or s.clusterId = :clusterId)
        order by s.updatedAt desc
        """,
    )
    fun findAllVisibleTo(@Param("userId") userId: UUID, @Param("clusterId") clusterId: UUID?): List<ChatSession>

    @Query("select s from ChatSession s join ChatRun r on r.sessionId = s.id where r.id = :runId")
    fun findByRunId(@Param("runId") runId: UUID): ChatSession?

    fun existsByClusterId(clusterId: UUID): Boolean

    @Query(
        value = """
        select s.id from tbl_chat_session s join tbl_cluster c on c.id = s.cluster_id
        where c.team_id = :teamId order by s.id for update of s
        """,
        nativeQuery = true,
    )
    fun lockIdsByTeamId(@Param("teamId") teamId: UUID): List<UUID>

    @Modifying
    @Query("delete from ChatSession s where s.clusterId in (select c.id from Cluster c where c.teamId = :teamId)")
    fun deleteAllByTeamId(@Param("teamId") teamId: UUID)
}

fun ChatSessionRepository.findByIdOrThrow(id: UUID): ChatSession =
    findByIdOrNull(id) ?: throw ChatNotFoundException()

fun ChatSessionRepository.lockByIdOrThrow(id: UUID): ChatSession =
    lockById(id) ?: throw ChatNotFoundException()
