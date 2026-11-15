package com.duro.kukie.hosting.exception

import com.duro.kukie.global.exception.BusinessException

class HostingTooManyRequestsException : BusinessException(HostingErrorCode.HOSTING_TOO_MANY_REQUESTS)
