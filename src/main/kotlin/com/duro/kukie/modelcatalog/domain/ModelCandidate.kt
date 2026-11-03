package com.duro.kukie.modelcatalog.domain

import com.duro.kukie.global.entity.BaseTimeEntity
import com.github.f4b6a3.uuid.UuidCreator
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "tbl_model_candidate")
class ModelCandidate(
    modelId: String,
    collected: CollectedModel,
    displayOrder: Int,
    collectedAt: LocalDateTime,
) : BaseTimeEntity() {

    @Id
    var id: UUID = UuidCreator.getTimeOrderedEpoch()
        protected set

    @Column(name = "model_id", nullable = false, length = MAX_MODEL_ID_LENGTH)
    var modelId = modelId
        protected set

    @Column(name = "params_b", nullable = false)
    var paramsB = collected.paramsB
        protected set

    @Column(name = "size_gb")
    var sizeGb = collected.sizeGb
        protected set

    @Column(name = "weight_gb")
    var weightGb = collected.weightGb
        protected set

    @Column(nullable = false, length = MAX_ARCHITECTURE_LENGTH)
    var architecture = collected.architecture
        protected set

    @Column(nullable = false, length = MAX_LICENSE_LENGTH)
    var license = collected.license
        protected set

    @Column(name = "license_note", length = 50)
    var licenseNote = collected.licenseNote
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "vllm_support", nullable = false, length = 20)
    var vllmSupport = collected.vllmSupport
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var gated = collected.gated
        protected set

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "fits_gpus", nullable = false, columnDefinition = "jsonb")
    var fitsGpus: List<String> = collected.fitsGpus
        protected set

    @Column(nullable = false)
    var likes = collected.likes
        protected set

    @Column(name = "registered_on")
    var registeredOn: LocalDate? = collected.registeredOn
        protected set

    @Column(name = "display_order", nullable = false)
    var displayOrder = displayOrder
        protected set

    @Column(name = "collected_at", nullable = false)
    var collectedAt = collectedAt
        protected set

    @Column(name = "previous_collected_at")
    var previousCollectedAt: LocalDateTime? = null
        protected set

    fun refresh(collected: CollectedModel, displayOrder: Int, collectedAt: LocalDateTime) {
        paramsB = collected.paramsB
        sizeGb = collected.sizeGb
        weightGb = collected.weightGb
        architecture = collected.architecture
        license = collected.license
        licenseNote = collected.licenseNote
        vllmSupport = collected.vllmSupport
        gated = collected.gated
        fitsGpus = collected.fitsGpus
        likes = collected.likes
        registeredOn = collected.registeredOn
        this.displayOrder = displayOrder
        previousCollectedAt = this.collectedAt
        this.collectedAt = collectedAt
    }

    companion object {
        const val MAX_MODEL_ID_LENGTH = 200
        const val MAX_ARCHITECTURE_LENGTH = 200
        const val MAX_LICENSE_LENGTH = 100
    }
}
