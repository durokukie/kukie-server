package com.duro.kukie.hosting.exception

import com.duro.kukie.global.exception.BusinessException

class HostingRepositoryNotFoundException : BusinessException(HostingErrorCode.HOSTING_REPOSITORY_NOT_FOUND)
