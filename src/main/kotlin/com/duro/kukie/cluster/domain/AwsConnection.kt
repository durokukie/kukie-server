package com.duro.kukie.cluster.domain

import com.duro.kukie.cluster.exception.AwsAccountMismatchException
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

/** 팀이 고객 AWS 계정의 KukieAccess-<externalId> 역할을 맡을 때 쓰는 ExternalId. 연결한 AWS 계정마다 하나다. */
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

    /** 연결을 해제하기 전 계정. 고객이 스택을 지웠는지(역할이 사라졌는지) 확인할 때 쓴다. 다시 연결하면 비운다. */
    @Column(name = "last_account_id", length = 12)
    var lastAccountId: String? = null
        protected set

    /** 연결을 해제하기 전 스택 ARN. 해제 뒤에도 지울 스택의 콘솔 주소를 만든다. */
    @Column(name = "last_stack_id", length = 512)
    var lastStackId: String? = null
        protected set

    /** 해제한 스택의 역할이 사라진 것을 확인한 때. 확인한 뒤에는 AWS 에 다시 묻지 않는다. */
    @Column(name = "last_stack_deleted_at")
    var lastStackDeletedAt: LocalDateTime? = null
        protected set

    val connected: Boolean
        get() = accountId != null

    /** 연결된 계정과 다른 계정으로 EKS 를 찾거나 등록하지 못하게 한다. 아직 모르는 계정(v1 직접 입력)은 받는다. */
    fun requireAccount(accountId: String) {
        if (this.accountId != null && this.accountId != accountId) throw AwsAccountMismatchException()
    }

    fun connect(accountId: String, at: LocalDateTime, stackId: String?) {
        this.accountId = accountId
        this.connectedAt = at
        this.stackId = stackId
        this.lastAccountId = null
        this.lastStackId = null
        this.lastStackDeletedAt = null
    }

    /**
     * 연결 해제. 다시 연결할 때 같은 스택 이름을 쓰도록 ExternalId 는 그대로 둔다. 계정과 스택은 last 칸으로 옮겨
     * 고객이 스택을 지웠는지 확인할 수 있게 한다. 연결되지 않은 채 다시 해제하면 앞서 옮긴 값을 그대로 둔다.
     */
    fun disconnect() {
        if (accountId != null) {
            this.lastAccountId = accountId
            this.lastStackId = stackId
            this.lastStackDeletedAt = null
        }
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
