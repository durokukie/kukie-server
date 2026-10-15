package com.duro.kukie.cluster.domain

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter(autoApply = true)
class ClusterStatusConverter : AttributeConverter<ClusterStatus, String> {

    override fun convertToDatabaseColumn(attribute: ClusterStatus): String = attribute.value

    override fun convertToEntityAttribute(dbData: String): ClusterStatus = ClusterStatus.from(dbData)
}
