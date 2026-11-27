package com.duro.kukie.report.exception

import com.duro.kukie.global.exception.BusinessException

class ReportNotReadyException : BusinessException(ReportErrorCode.REPORT_NOT_READY)
