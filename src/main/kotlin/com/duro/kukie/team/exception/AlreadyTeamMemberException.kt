package com.duro.kukie.team.exception

import com.duro.kukie.global.exception.BusinessException

class AlreadyTeamMemberException : BusinessException(TeamErrorCode.ALREADY_TEAM_MEMBER)
