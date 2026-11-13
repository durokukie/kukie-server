package com.duro.kukie.hosting.exception

import com.duro.kukie.global.exception.BusinessException

class HostingAppLimitException(message: String = HostingErrorCode.HOSTING_APP_LIMIT.message) : BusinessException(HostingErrorCode.HOSTING_APP_LIMIT, message)
