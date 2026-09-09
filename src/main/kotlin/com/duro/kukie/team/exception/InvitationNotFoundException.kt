package com.duro.kukie.team.exception

import com.duro.kukie.global.exception.BusinessException

class InvitationNotFoundException : BusinessException(TeamErrorCode.INVITATION_NOT_FOUND)
