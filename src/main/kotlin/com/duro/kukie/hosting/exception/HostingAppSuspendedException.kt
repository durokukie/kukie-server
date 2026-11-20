package com.duro.kukie.hosting.exception

import com.duro.kukie.global.exception.BusinessException

class HostingAppSuspendedException : BusinessException(HostingErrorCode.HOSTING_APP_SUSPENDED, HostingErrorCode.HOSTING_APP_SUSPENDED.message)
