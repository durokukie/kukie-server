package com.duro.kukie.modelcatalog.domain

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ModelCandidateRepository : JpaRepository<ModelCandidate, UUID> {

    fun findAllByOrderByDisplayOrder(): List<ModelCandidate>
}
