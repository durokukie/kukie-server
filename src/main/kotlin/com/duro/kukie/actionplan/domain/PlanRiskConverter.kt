package com.duro.kukie.actionplan.domain

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter(autoApply = true)
class PlanRiskConverter : AttributeConverter<PlanRisk, String> {

    override fun convertToDatabaseColumn(attribute: PlanRisk): String = attribute.value

    override fun convertToEntityAttribute(dbData: String): PlanRisk = PlanRisk.from(dbData)
}
