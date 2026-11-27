package com.duro.kukie.report.domain

import com.duro.kukie.chat.domain.ChatSession
import com.duro.kukie.chat.domain.ChatVisibility
import com.duro.kukie.cluster.domain.Cluster
import com.duro.kukie.global.entity.BaseTimeEntity
import com.duro.kukie.global.util.withoutNul
import com.github.f4b6a3.uuid.UuidCreator
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

@Entity
@Table(name = "tbl_report")
class Report private constructor(
    kind: ReportKind,
    clusterId: UUID,
    conversationId: UUID?,
    ownerId: UUID?,
    shared: Boolean,
    title: String,
    periodStart: LocalDateTime?,
    periodEnd: LocalDateTime?,
    timeZone: String,
) : BaseTimeEntity() {

    @Id
    var id: UUID = UuidCreator.getTimeOrderedEpoch()
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var kind = kind
        protected set

    @Column(name = "cluster_id", nullable = false)
    var clusterId = clusterId
        protected set

    /** 대화가 지워져도 보고서는 남으므로 FK 가 없다. */
    @Column(name = "conversation_id")
    var conversationId = conversationId
        protected set

    /** 자동으로 만든 주간 보고서는 null. */
    @Column(name = "owner_id")
    var ownerId = ownerId
        protected set

    @Column(nullable = false)
    var shared = shared
        protected set

    @Column(nullable = false, length = 200)
    var title = title
        protected set

    @Column(columnDefinition = "text")
    var content: String? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status = ReportStatus.GENERATING
        protected set

    @Column(name = "period_start")
    var periodStart = periodStart
        protected set

    @Column(name = "period_end")
    var periodEnd = periodEnd
        protected set

    @Column(name = "through_turn")
    var throughTurn: Int? = null
        protected set

    @Column(name = "ai_refined", nullable = false)
    var aiRefined = false
        protected set

    @Column(name = "time_zone", nullable = false, length = 50)
    var timeZone = timeZone
        protected set

    @Column(nullable = false)
    var version = 1
        protected set

    @Column(name = "edited_by")
    var editedBy: UUID? = null
        protected set

    @Column(name = "edited_at")
    var editedAt: LocalDateTime? = null
        protected set

    val isGenerating: Boolean
        get() = status == ReportStatus.GENERATING

    val isScheduled: Boolean
        get() = kind == ReportKind.WEEKLY && ownerId == null

    fun complete(content: String, aiRefined: Boolean, throughTurn: Int?) {
        check(isGenerating)
        this.content = content.withoutNul()
        this.aiRefined = aiRefined
        this.throughTurn = throughTurn
        status = ReportStatus.READY
    }

    fun edit(title: String, content: String, userId: UUID, now: LocalDateTime) {
        this.title = title.withoutNul()
        this.content = content.withoutNul()
        editedBy = userId
        editedAt = now
        version += 1
    }

    companion object {
        const val TITLE_LIMIT = 200

        fun ofConversation(session: ChatSession, ownerId: UUID, zone: ZoneId) = Report(
            kind = ReportKind.CONVERSATION,
            clusterId = session.clusterId,
            conversationId = session.id,
            ownerId = ownerId,
            shared = session.visibility == ChatVisibility.SHARED,
            title = session.title,
            periodStart = null,
            periodEnd = null,
            timeZone = zone.id,
        )

        fun weekly(cluster: Cluster, ownerId: UUID?, periodStart: LocalDateTime, periodEnd: LocalDateTime, zone: ZoneId) = Report(
            kind = ReportKind.WEEKLY,
            clusterId = cluster.id,
            conversationId = null,
            ownerId = ownerId,
            shared = true,
            title = "${cluster.name} 주간 보고서 (${dateOf(periodStart, zone)} ~ ${dateOf(periodEnd, zone)})".take(TITLE_LIMIT),
            periodStart = periodStart,
            periodEnd = periodEnd,
            timeZone = zone.id,
        )

        /** 168시간이 아니라 그 시간대의 일주일 전이다. 서머타임이 바뀌는 주에도 앞 회차와 빈틈없이 이어진다. */
        fun weekBefore(utc: LocalDateTime, zone: ZoneId): LocalDateTime =
            utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).minusWeeks(1)
                .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()

        private fun dateOf(utc: LocalDateTime, zone: ZoneId): LocalDate =
            utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).toLocalDate()
    }
}
