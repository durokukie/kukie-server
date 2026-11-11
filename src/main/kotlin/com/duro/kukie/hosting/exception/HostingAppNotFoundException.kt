package com.duro.kukie.hosting.exception

import com.duro.kukie.global.exception.BusinessException

class HostingAppNotFoundException : BusinessException(HostingErrorCode.HOSTING_APP_NOT_FOUND)
