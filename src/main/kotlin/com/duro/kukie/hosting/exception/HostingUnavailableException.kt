package com.duro.kukie.hosting.exception

import com.duro.kukie.global.exception.BusinessException

class HostingUnavailableException : BusinessException(HostingErrorCode.HOSTING_UNAVAILABLE)
