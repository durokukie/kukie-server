package com.duro.kukie.chat.domain

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter(autoApply = true)
class RunKindConverter : AttributeConverter<RunKind, String> {

    override fun convertToDatabaseColumn(attribute: RunKind): String = attribute.value

    override fun convertToEntityAttribute(dbData: String): RunKind = RunKind.from(dbData)
}
