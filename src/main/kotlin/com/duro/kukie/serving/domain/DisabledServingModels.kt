package com.duro.kukie.serving.domain

import java.util.UUID

interface DisabledServingModels {
    fun find(clusterId: UUID): Set<String>
    fun setEnabled(clusterId: UUID, model: String, enabled: Boolean)
}
