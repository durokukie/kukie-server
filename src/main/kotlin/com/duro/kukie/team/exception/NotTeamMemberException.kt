package com.duro.kukie.team.exception

import com.duro.kukie.global.exception.BusinessException

class NotTeamMemberException : BusinessException(TeamErrorCode.NOT_TEAM_MEMBER)
