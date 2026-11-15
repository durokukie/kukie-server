package com.duro.kukie.serving.domain

import com.duro.kukie.global.entity.BaseTimeEntity
import com.github.f4b6a3.uuid.UuidCreator
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.util.UUID

@Entity
@Table(name = "tbl_serving_endpoint")
class ServingEndpoint(clusterId: UUID, baseUrl: String, encryptedToken: String, models: List<String>) : BaseTimeEntity() {
    @Id var id: UUID = UuidCreator.getTimeOrderedEpoch()
        protected set
    @Column(name = "cluster_id", nullable = false, unique = true)
    var clusterId = clusterId
        protected set
    @Column(name = "base_url", nullable = false, length = 512)
    var baseUrl = baseUrl
        protected set
    @Column(name = "encrypted_token", nullable = false, columnDefinition = "text")
    var encryptedToken = encryptedToken
        protected set
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    var models = models
        protected set

    fun configure(baseUrl: String, encryptedToken: String, models: List<String>) {
        this.baseUrl = baseUrl
        this.encryptedToken = encryptedToken
        this.models = models.toList()
    }
}
