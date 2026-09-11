package com.duro.kukie.team.exception

import com.duro.kukie.global.exception.BusinessException

class CannotRemoveSelfException : BusinessException(TeamErrorCode.CANNOT_REMOVE_SELF)
