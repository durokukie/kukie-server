package com.duro.kukie.cluster.domain

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter(autoApply = true)
class AccessLevelConverter : AttributeConverter<AccessLevel?, String?> {

    override fun convertToDatabaseColumn(attribute: AccessLevel?): String? = attribute?.value

    override fun convertToEntityAttribute(dbData: String?): AccessLevel? = dbData?.let(AccessLevel::from)
}
