package com.duro.kukie.hosting.exception

import com.duro.kukie.global.exception.BusinessException

class HostingAppSourceMismatchException(message: String = HostingErrorCode.HOSTING_APP_SOURCE_MISMATCH.message) :
    BusinessException(HostingErrorCode.HOSTING_APP_SOURCE_MISMATCH, message)
