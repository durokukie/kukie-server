package com.duro.kukie.hosting.exception

import com.duro.kukie.global.exception.BusinessException

class HostingDeploymentNotFoundException : BusinessException(HostingErrorCode.HOSTING_DEPLOYMENT_NOT_FOUND)
