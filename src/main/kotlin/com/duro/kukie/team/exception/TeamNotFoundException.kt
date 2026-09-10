package com.duro.kukie.team.exception

import com.duro.kukie.global.exception.BusinessException

class TeamNotFoundException : BusinessException(TeamErrorCode.TEAM_NOT_FOUND)
