package com.duro.kukie.serving.application

import com.duro.kukie.global.exception.BusinessException
import com.duro.kukie.global.exception.GlobalErrorCode
import com.duro.kukie.global.util.utcNow
import java.time.LocalDate

internal fun usageWindow(from: LocalDate?, to: LocalDate?): Pair<LocalDate, LocalDate> {
    val today = utcNow().toLocalDate()
    val end = to ?: today
    val start = from ?: end.minusDays(6)
    if (start.isAfter(end) || end.isAfter(today) || start.isBefore(today.minusDays(89))) throw BusinessException(GlobalErrorCode.BAD_REQUEST)
    return start to end
}
