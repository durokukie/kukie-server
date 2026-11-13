package com.duro.kukie.hosting.exception

import com.duro.kukie.global.exception.BusinessException

class HostingInvalidAppNameException(message: String = HostingErrorCode.HOSTING_INVALID_APP_NAME.message) : BusinessException(HostingErrorCode.HOSTING_INVALID_APP_NAME, message)
