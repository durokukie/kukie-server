package com.duro.kukie.cluster.domain

import com.duro.kukie.global.entity.BaseTimeEntity
import com.github.f4b6a3.uuid.UuidCreator
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.security.SecureRandom
import java.time.LocalDateTime
import java.util.HexFormat
import java.util.UUID

/** 팀이 고객 AWS 계정의 KukieAccess-<externalId> 역할을 맡을 때 쓰는 ExternalId. 팀마다 하나다. */
@Entity
@Table(name = "tbl_aws_connection")
class AwsConnection(
    teamId: UUID,
    createdBy: UUID,
    externalId: String = newExternalId(),
) : BaseTimeEntity() {

    @Id
    var id: UUID = UuidCreator.getTimeOrderedEpoch()
        protected set

    @Column(name = "team_id", nullable = false)
    var teamId = teamId
        protected set

    @Column(name = "external_id", nullable = false, length = 64)
    var externalId = externalId
        protected set

    @Column(name = "created_by", nullable = false)
    var createdBy = createdBy
        protected set

    /** 템플릿 v2 스택이 알려 주고 서버가 역할을 맡아 확인한 계정. v1 로 연결했거나 아직 알림이 없으면 null. */
    @Column(name = "account_id", length = 12)
    var accountId: String? = null
        protected set

    @Column(name = "connected_at")
    var connectedAt: LocalDateTime? = null
        protected set

    /** 계정을 알려 준 스택의 ARN. 연결 해제 때 고객이 지울 스택을 콘솔에서 바로 열게 한다. 이 칸이 생기기 전에 연결했으면 null. */
    @Column(name = "stack_id", length = 512)
    var stackId: String? = null
        protected set

    val connected: Boolean
        get() = accountId != null

    fun connect(accountId: String, at: LocalDateTime, stackId: String?) {
        this.accountId = accountId
        this.connectedAt = at
        this.stackId = stackId
    }

    /** 연결 해제. 다시 연결할 때 같은 스택 이름을 쓰도록 ExternalId 는 그대로 둔다. */
    fun disconnect() {
        this.accountId = null
        this.connectedAt = null
        this.stackId = null
    }

    companion object {
        private const val PREFIX = "kukie-"
        private const val RANDOM_BYTES = 8
        private val random = SecureRandom()

        /** 템플릿의 ExternalId 패턴(^kukie-[a-z0-9]{8,40}$)에 맞춘다. */
        fun newExternalId(): String =
            PREFIX + HexFormat.of().formatHex(ByteArray(RANDOM_BYTES).also(random::nextBytes))
    }
}
