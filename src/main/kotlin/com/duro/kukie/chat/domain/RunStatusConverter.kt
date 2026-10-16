package com.duro.kukie.chat.domain

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter(autoApply = true)
class RunStatusConverter : AttributeConverter<RunStatus, String> {

    override fun convertToDatabaseColumn(attribute: RunStatus): String = attribute.value

    override fun convertToEntityAttribute(dbData: String): RunStatus = RunStatus.from(dbData)
}
