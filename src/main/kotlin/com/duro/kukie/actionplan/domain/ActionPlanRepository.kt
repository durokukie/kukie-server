package com.duro.kukie.actionplan.domain

import com.duro.kukie.actionplan.exception.ActionPlanNotFoundException
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.findByIdOrNull
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ActionPlanRepository : JpaRepository<ActionPlan, UUID> {

    fun findAllByRunIdOrderByCreatedAtAscIdAsc(runId: UUID): List<ActionPlan>

    fun findAllByRunIdInOrderByCreatedAtAscIdAsc(runIds: Collection<UUID>): List<ActionPlan>

    fun findAllByRunIdAndStatusInOrderByCreatedAtAscIdAsc(runId: UUID, statuses: Collection<PlanStatus>): List<ActionPlan>

    fun findByRunIdAndToolCallId(runId: UUID, toolCallId: String): ActionPlan?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ActionPlan p where p.id = :id")
    fun lockById(@Param("id") id: UUID): ActionPlan?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ActionPlan p where p.runId = :runId and p.status in :statuses order by p.id")
    fun lockAllByRunIdAndStatusIn(
        @Param("runId") runId: UUID,
        @Param("statuses") statuses: Collection<PlanStatus>,
    ): List<ActionPlan>

    @Query(value = "select count(*) from (select pg_advisory_xact_lock(hashtext(:key))) as locked", nativeQuery = true)
    fun lockCodeGeneration(@Param("key") key: String): Long

    @Query("select p.code from ActionPlan p where p.code like :pattern")
    fun findCodesLike(@Param("pattern") pattern: String): List<String>

    /** 범위가 ChatSessionRepository.findAllVisibleTo 와 같아야 한다. */
    @Query(
        """
        select new com.duro.kukie.actionplan.domain.VisibleActionPlan(p, s.clusterId, s.userId, u.name)
        from ActionPlan p
            join ChatRun r on r.id = p.runId
            join ChatSession s on s.id = r.sessionId
            join Cluster c on c.id = s.clusterId
            join User u on u.id = s.userId
        where ((s.userId = :userId and s.visibility = com.duro.kukie.chat.domain.ChatVisibility.PRIVATE)
            or (s.visibility = com.duro.kukie.chat.domain.ChatVisibility.SHARED
                and c.teamId in (select m.teamId from TeamMembership m where m.userId = :userId)))
          and (:clusterId is null or s.clusterId = :clusterId)
          and (:status is null or p.status = :status)
        order by p.updatedAt desc
        """,
    )
    fun findAllVisibleTo(
        @Param("userId") userId: UUID,
        @Param("clusterId") clusterId: UUID?,
        @Param("status") status: PlanStatus?,
    ): List<VisibleActionPlan>

    @Query("select p.runId as runId, count(p) as plans from ActionPlan p where p.runId in :runIds group by p.runId")
    fun countByRunIds(@Param("runIds") runIds: Collection<UUID>): List<RunPlanCount>

    /** 그 도구의 계획 중 끝나지 않은 것: 결과 없이 승인·실행 상태이거나, 머지는 기록됐는데 sync 를 아직 지켜보는 중이다. */
    @Query(
        value = """
        select p.id as planId, s.cluster_id as clusterId, r.status in ('running', 'awaiting_approval', 'recovery_required') as runActive
        from tbl_action_plan p
            join tbl_chat_run r on r.id = p.run_id
            join tbl_chat_session s on s.id = r.session_id
        where p.tool_name = :toolName
          and (p.status in ('APPROVED', 'EXECUTING')
            or (p.status = 'APPLIED' and p.execution_result -> 'sync_result' ->> 'status' = :watching))
        order by p.id
        """,
        nativeQuery = true,
    )
    fun findUnsettled(@Param("toolName") toolName: String, @Param("watching") watching: String): List<UnsettledPlanRow>

    @Modifying
    @Query(
        """
        delete from ActionPlan p where p.runId in (
            select r.id from ChatRun r where r.sessionId in (
                select s.id from ChatSession s where s.clusterId in (select c.id from Cluster c where c.teamId = :teamId)))
        """,
    )
    fun deleteAllByTeamId(@Param("teamId") teamId: UUID)

    @Modifying
    @Query(
        """
        delete from ActionPlan p where p.runId in (
            select r.id from ChatRun r where r.sessionId in (select s.id from ChatSession s where s.clusterId = :clusterId))
        """,
    )
    fun deleteAllByClusterId(@Param("clusterId") clusterId: UUID)

    @Modifying
    @Query("delete from ActionPlan p where p.runId in (select r.id from ChatRun r where r.sessionId = :sessionId)")
    fun deleteAllBySessionId(@Param("sessionId") sessionId: UUID)
}

fun ActionPlanRepository.findByIdOrThrow(id: UUID): ActionPlan =
    findByIdOrNull(id) ?: throw ActionPlanNotFoundException()

fun ActionPlanRepository.lockByIdOrThrow(id: UUID): ActionPlan =
    lockById(id) ?: throw ActionPlanNotFoundException()

fun ActionPlanRepository.closeOpenPlans(runId: UUID): List<ActionPlan> =
    lockAllByRunIdAndStatusIn(runId, PlanStatus.OPEN).onEach(ActionPlan::close)
