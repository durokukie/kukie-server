package com.duro.kukie.report.exception

import com.duro.kukie.global.exception.BusinessException

class TooManyReportsException : BusinessException(ReportErrorCode.REPORT_TOO_MANY)
