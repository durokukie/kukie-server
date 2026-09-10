package com.duro.kukie.team.exception

import com.duro.kukie.global.exception.BusinessException

class AdminRequiredException : BusinessException(TeamErrorCode.ADMIN_REQUIRED)
