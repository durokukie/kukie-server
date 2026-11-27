package com.duro.kukie.report.domain

import com.duro.kukie.report.exception.ReportNotFoundException
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.findByIdOrNull
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
import java.util.UUID

interface ReportRepository : JpaRepository<Report, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Report r where r.id = :id")
    fun lockById(@Param("id") id: UUID): Report?

    /** ReportPermission 의 읽기 규칙과 같아야 한다. */
    @Query(
        """
        select new com.duro.kukie.report.domain.ReportSummary(
            r.id, r.kind, r.clusterId, r.conversationId, r.ownerId, r.shared, r.title, r.status,
            r.periodStart, r.periodEnd, r.aiRefined, r.version, r.createdAt, r.updatedAt)
        from Report r join Cluster c on c.id = r.clusterId
        where ((r.shared = false and r.ownerId = :userId)
            or (r.shared = true and c.teamId in (select m.teamId from TeamMembership m where m.userId = :userId)))
          and (:clusterId is null or r.clusterId = :clusterId)
        order by r.createdAt desc
        """,
    )
    fun findAllVisibleTo(@Param("userId") userId: UUID, @Param("clusterId") clusterId: UUID?): List<ReportSummary>

    /** 대화를 볼 수 있는 사람만 부른다 — Shared 보고서의 팀 구성원 여부를 다시 보지 않는다. */
    @Query(
        """
        select r from Report r
        where r.conversationId = :conversationId
          and r.kind = com.duro.kukie.report.domain.ReportKind.CONVERSATION
          and (r.shared = true or r.ownerId = :userId)
          and ((r.status = com.duro.kukie.report.domain.ReportStatus.READY and coalesce(r.throughTurn, 0) = :lastTurn)
            or (r.status = com.duro.kukie.report.domain.ReportStatus.GENERATING and r.createdAt > :generatingSince))
        order by r.createdAt desc
        """,
    )
    fun findUpToDateOfConversation(
        @Param("conversationId") conversationId: UUID,
        @Param("userId") userId: UUID,
        @Param("lastTurn") lastTurn: Int,
        @Param("generatingSince") generatingSince: LocalDateTime,
    ): List<Report>

    @Query(
        """
        select count(r) > 0 from Report r
        where r.clusterId = :clusterId and r.periodEnd = :periodEnd
          and r.kind = com.duro.kukie.report.domain.ReportKind.WEEKLY and r.ownerId is null
        """,
    )
    fun existsScheduled(@Param("clusterId") clusterId: UUID, @Param("periodEnd") periodEnd: LocalDateTime): Boolean

    @Query(
        """
        select max(r.periodEnd) from Report r
        where r.clusterId = :clusterId
          and r.kind = com.duro.kukie.report.domain.ReportKind.WEEKLY and r.ownerId is null
        """,
    )
    fun findLastScheduledEnd(@Param("clusterId") clusterId: UUID): LocalDateTime?

    /** 만든 지 오래된 행은 다시 해도 같은 이유로 실패한다고 보고 넘기지 않는다. */
    @Query(
        value = """
        select id from tbl_report
        where status = 'GENERATING' and updated_at < :stalledBefore and created_at > :createdAfter
        order by id limit :limit for update skip locked
        """,
        nativeQuery = true,
    )
    fun lockStalledIds(
        @Param("stalledBefore") stalledBefore: LocalDateTime,
        @Param("createdAfter") createdAfter: LocalDateTime,
        @Param("limit") limit: Int,
    ): List<UUID>

    @Modifying(clearAutomatically = true)
    @Query("update Report r set r.updatedAt = :now where r.id in :ids")
    fun touch(@Param("ids") ids: Collection<UUID>, @Param("now") now: LocalDateTime)

    @Modifying
    @Query("delete from Report r where r.clusterId in (select c.id from Cluster c where c.teamId = :teamId)")
    fun deleteAllByTeamId(@Param("teamId") teamId: UUID)

    @Modifying
    @Query("delete from Report r where r.clusterId = :clusterId")
    fun deleteAllByClusterId(@Param("clusterId") clusterId: UUID)

    @Modifying
    @Query("delete from Report r where r.ownerId = :ownerId and r.shared = false")
    fun deleteAllPrivateByOwnerId(@Param("ownerId") ownerId: UUID)
}

fun ReportRepository.findByIdOrThrow(id: UUID): Report =
    findByIdOrNull(id) ?: throw ReportNotFoundException()

fun ReportRepository.lockByIdOrThrow(id: UUID): Report =
    lockById(id) ?: throw ReportNotFoundException()
