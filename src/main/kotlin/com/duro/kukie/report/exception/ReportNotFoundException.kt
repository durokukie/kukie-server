package com.duro.kukie.report.exception

import com.duro.kukie.global.exception.BusinessException

class ReportNotFoundException : BusinessException(ReportErrorCode.REPORT_NOT_FOUND)
