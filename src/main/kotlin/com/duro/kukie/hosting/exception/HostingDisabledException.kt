package com.duro.kukie.hosting.exception

import com.duro.kukie.global.exception.BusinessException

class HostingDisabledException : BusinessException(HostingErrorCode.HOSTING_DISABLED)
