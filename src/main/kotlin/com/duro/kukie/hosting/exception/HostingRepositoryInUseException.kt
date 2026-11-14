package com.duro.kukie.hosting.exception

import com.duro.kukie.global.exception.BusinessException

class HostingRepositoryInUseException : BusinessException(HostingErrorCode.HOSTING_REPOSITORY_IN_USE)
