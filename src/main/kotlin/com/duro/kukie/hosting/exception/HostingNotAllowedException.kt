package com.duro.kukie.hosting.exception

import com.duro.kukie.global.exception.BusinessException

class HostingNotAllowedException : BusinessException(HostingErrorCode.HOSTING_NOT_ALLOWED)
