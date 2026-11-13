package com.duro.kukie.hosting.exception

import com.duro.kukie.global.exception.BusinessException

class HostingDeployNotFoundException(message: String = HostingErrorCode.HOSTING_DEPLOY_NOT_FOUND.message) : BusinessException(HostingErrorCode.HOSTING_DEPLOY_NOT_FOUND, message)
