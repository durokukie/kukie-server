package com.duro.kukie.hosting.exception

import com.duro.kukie.global.exception.BusinessException

class HostingLogNotFoundException : BusinessException(HostingErrorCode.HOSTING_LOG_NOT_FOUND)
