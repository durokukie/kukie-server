package com.duro.kukie.hosting.domain

import com.duro.kukie.global.entity.BaseTimeEntity
import com.duro.kukie.global.util.utcNow
import com.github.f4b6a3.uuid.UuidCreator
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

/** 코딩 에이전트가 사용자 컴퓨터에 두고 쓰는 배포 키. 처음 한 번 웹에서 허용하면 받고, 30일 쓰지 않으면 만료된다. */
@Entity
@Table(name = "tbl_hosting_agent_key")
class HostingAgentKey(
    userId: UUID,
    keyHash: String,
) : BaseTimeEntity() {

    @Id
    var id: UUID = UuidCreator.getTimeOrderedEpoch()
        protected set

    @Column(name = "user_id", nullable = false)
    var userId = userId
        protected set

    @Column(name = "key_hash", nullable = false, length = 64)
    var keyHash = keyHash
        protected set

    @Column(name = "last_used_at", nullable = false)
    var lastUsedAt: LocalDateTime = utcNow()
        protected set

    fun expired(now: LocalDateTime): Boolean = lastUsedAt.isBefore(now.minus(UNUSED_LIMIT))

    /** 상태 확인은 몇 초마다 오므로 요청마다 쓰지 않게 1분에 한 번만 적는다. */
    fun staleUse(now: LocalDateTime): Boolean = lastUsedAt.isBefore(now.minus(USE_RECORD_INTERVAL))

    companion object {
        val UNUSED_LIMIT: Duration = Duration.ofDays(30)
        val USE_RECORD_INTERVAL: Duration = Duration.ofMinutes(1)
        const val MAX_PER_USER = 10
    }
}

interface HostingAgentKeyRepository : JpaRepository<HostingAgentKey, UUID> {

    fun findByKeyHash(keyHash: String): HostingAgentKey?

    fun findAllByUserIdOrderByLastUsedAtDesc(userId: UUID): List<HostingAgentKey>

    @Modifying
    @Query("update HostingAgentKey k set k.lastUsedAt = :now where k.id = :id")
    fun recordUse(id: UUID, now: LocalDateTime): Int

    @Modifying
    fun deleteByIdAndUserId(id: UUID, userId: UUID): Long

    @Modifying
    fun deleteByUserId(userId: UUID): Long
}
