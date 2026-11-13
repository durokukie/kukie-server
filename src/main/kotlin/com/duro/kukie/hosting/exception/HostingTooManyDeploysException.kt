package com.duro.kukie.hosting.exception

import com.duro.kukie.global.exception.BusinessException

class HostingTooManyDeploysException(message: String = HostingErrorCode.HOSTING_TOO_MANY_DEPLOYS.message) : BusinessException(HostingErrorCode.HOSTING_TOO_MANY_DEPLOYS, message)
