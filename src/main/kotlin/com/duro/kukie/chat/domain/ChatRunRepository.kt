package com.duro.kukie.chat.domain

import com.duro.kukie.chat.exception.ActiveRunExistsException
import com.duro.kukie.chat.exception.RunNotFoundException
import jakarta.persistence.LockModeType
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.findByIdOrNull
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ChatRunRepository : JpaRepository<ChatRun, UUID> {

    fun findBySessionIdAndRequestId(sessionId: UUID, requestId: String): ChatRun?

    fun findFirstBySessionIdAndStatusIn(sessionId: UUID, statuses: Collection<RunStatus>): ChatRun?

    fun existsBySessionIdAndStatusIn(sessionId: UUID, statuses: Collection<RunStatus>): Boolean

    @Query("select r.sessionId from ChatRun r where r.id = :id")
    fun findSessionIdById(@Param("id") id: UUID): UUID?

    @Query("select max(r.turnNo) from ChatRun r where r.sessionId = :sessionId")
    fun findMaxTurnNo(@Param("sessionId") sessionId: UUID): Int?

    @Query(
        """
        select new com.duro.kukie.chat.domain.RunTurn(
            r.id, r.turnNo, r.kind, r.mode, r.status, r.inputText, r.responsePayload, r.startedAt, r.finishedAt)
        from ChatRun r where r.sessionId = :sessionId order by r.turnNo
        """,
    )
    fun findTurnsBySessionId(@Param("sessionId") sessionId: UUID): List<RunTurn>

    fun findTop100BySessionIdAndStatusAndAgentMessagesIsNotNullOrderByTurnNoDesc(
        sessionId: UUID,
        status: RunStatus,
    ): List<ChatRun>

    @Query("select r.inputText from ChatRun r where r.sessionId = :sessionId and r.kind = :kind and r.id <> :exceptRunId")
    fun findInputTexts(
        @Param("sessionId") sessionId: UUID,
        @Param("kind") kind: RunKind,
        @Param("exceptRunId") exceptRunId: UUID,
    ): List<String?>

    @Query("select r.sessionId from ChatRun r where r.sessionId in :sessionIds and r.status = :status")
    fun findSessionIdsWithStatus(
        @Param("sessionIds") sessionIds: Collection<UUID>,
        @Param("status") status: RunStatus,
    ): List<UUID>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ChatRun r where r.id = :id")
    fun lockById(@Param("id") id: UUID): ChatRun?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ChatRun r where r.sessionId = :sessionId and r.status in :statuses order by r.id")
    fun lockAllBySessionIdAndStatusIn(
        @Param("sessionId") sessionId: UUID,
        @Param("statuses") statuses: Collection<RunStatus>,
    ): List<ChatRun>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ChatRun r where r.status = :status order by r.id")
    fun lockAllByStatus(@Param("status") status: RunStatus): List<ChatRun>

    @Query(
        value = """
        select r.id from tbl_chat_run r
        join tbl_chat_session s on s.id = r.session_id
        join tbl_cluster c on c.id = s.cluster_id
        where c.team_id = :teamId order by r.id for update of r
        """,
        nativeQuery = true,
    )
    fun lockIdsByTeamId(@Param("teamId") teamId: UUID): List<UUID>

    @Modifying
    @Query(
        """
        delete from ChatRun r where r.sessionId in (
            select s.id from ChatSession s where s.clusterId in (select c.id from Cluster c where c.teamId = :teamId))
        """,
    )
    fun deleteAllByTeamId(@Param("teamId") teamId: UUID)

    @Modifying
    @Query("delete from ChatRun r where r.sessionId = :sessionId")
    fun deleteAllBySessionId(@Param("sessionId") sessionId: UUID)
}

fun ChatRunRepository.findByIdOrThrow(id: UUID): ChatRun =
    findByIdOrNull(id) ?: throw RunNotFoundException()

fun ChatRunRepository.lockByIdOrThrow(id: UUID): ChatRun =
    lockById(id) ?: throw RunNotFoundException()

fun ChatRunRepository.findSessionIdOrThrow(id: UUID): UUID =
    findSessionIdById(id) ?: throw RunNotFoundException()

fun ChatRunRepository.flushOrThrowActiveRunExists() {
    try {
        flush()
    } catch (exception: DataIntegrityViolationException) {
        if (ACTIVE_RUN_INDEX in exception.mostSpecificCause.message.orEmpty()) throw ActiveRunExistsException()
        throw exception
    }
}

private const val ACTIVE_RUN_INDEX = "uk_chat_run_active"
