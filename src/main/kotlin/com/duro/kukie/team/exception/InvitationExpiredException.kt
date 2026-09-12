package com.duro.kukie.team.exception

import com.duro.kukie.global.exception.BusinessException

class InvitationExpiredException : BusinessException(TeamErrorCode.INVITATION_EXPIRED)
