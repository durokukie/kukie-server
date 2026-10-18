package com.duro.kukie.cluster.domain

import com.duro.kukie.global.entity.BaseTimeEntity
import com.github.f4b6a3.uuid.UuidCreator
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.HexFormat
import java.util.UUID

@Entity
@Table(name = "tbl_cluster")
class Cluster(
    teamId: UUID,
    registeredBy: UUID,
    name: String,
    apiServer: String,
    caData: String?,
    insecure: Boolean,
    credential: String,
    contextName: String,
    defaultNamespace: String,
    fingerprint: String,
    provider: ClusterProvider = ClusterProvider.GENERIC,
) : BaseTimeEntity() {

    @Id
    var id: UUID = UuidCreator.getTimeOrderedEpoch()
        protected set

    @Column(name = "team_id", nullable = false)
    var teamId = teamId
        protected set

    @Column(name = "registered_by", nullable = false)
    var registeredBy = registeredBy
        protected set

    @Column(nullable = false, length = 100)
    var name = name
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var provider = provider
        protected set

    @Column(name = "api_server", nullable = false, length = 300)
    var apiServer = apiServer
        protected set

    @Column(name = "ca_data", columnDefinition = "text")
    var caData = caData
        protected set

    @Column(nullable = false)
    var insecure = insecure
        protected set

    @Column(nullable = false, columnDefinition = "text")
    var credential = credential
        protected set

    /** 대화마다 복사해 두므로 kubeconfig 를 바꿔도 그대로 둔다. */
    @Column(name = "context_name", nullable = false, length = 200)
    var contextName = contextName
        protected set

    @Column(name = "default_namespace", nullable = false, length = 63)
    var defaultNamespace = defaultNamespace
        protected set

    @Column(nullable = false, length = 64)
    var fingerprint = fingerprint
        protected set

    @Column(nullable = false, length = 20)
    var status = ClusterStatus.DISCONNECTED
        protected set

    @Column(name = "last_checked_at")
    var lastCheckedAt: LocalDateTime? = null
        protected set

    fun rename(name: String) {
        this.name = name
    }

    fun changeDefaultNamespace(namespace: String) {
        this.defaultNamespace = namespace
    }

    fun replaceConnection(
        apiServer: String,
        caData: String?,
        insecure: Boolean,
        credential: String,
        fingerprint: String,
    ) {
        this.apiServer = apiServer
        this.caData = caData
        this.insecure = insecure
        this.credential = credential
        this.fingerprint = fingerprint
        this.status = ClusterStatus.DISCONNECTED
    }

    fun recordCheck(status: ClusterStatus, checkedAt: LocalDateTime) {
        this.status = status
        this.lastCheckedAt = checkedAt
    }

    companion object {
        fun fingerprintOf(apiServer: String, caData: String?): String =
            HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest("$apiServer\n${caData.orEmpty()}".toByteArray(Charsets.UTF_8)),
            )
    }
}
