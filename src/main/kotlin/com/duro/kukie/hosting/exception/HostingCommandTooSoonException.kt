package com.duro.kukie.hosting.exception

import com.duro.kukie.global.exception.BusinessException

class HostingCommandTooSoonException : BusinessException(HostingErrorCode.HOSTING_COMMAND_TOO_SOON)
