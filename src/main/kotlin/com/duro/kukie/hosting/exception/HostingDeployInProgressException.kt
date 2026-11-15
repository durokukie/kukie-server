package com.duro.kukie.hosting.exception

import com.duro.kukie.global.exception.BusinessException

class HostingDeployInProgressException(message: String = HostingErrorCode.HOSTING_DEPLOY_IN_PROGRESS.message) : BusinessException(HostingErrorCode.HOSTING_DEPLOY_IN_PROGRESS, message)
