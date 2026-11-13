package com.duro.kukie.hosting.exception

import com.duro.kukie.global.exception.BusinessException

class HostingDeployNotStartedException(message: String = HostingErrorCode.HOSTING_DEPLOY_NOT_STARTED.message) : BusinessException(HostingErrorCode.HOSTING_DEPLOY_NOT_STARTED, message)
