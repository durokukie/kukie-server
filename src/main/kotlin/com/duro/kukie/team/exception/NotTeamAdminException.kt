package com.duro.kukie.team.exception

import com.duro.kukie.global.exception.BusinessException

class NotTeamAdminException(
    message: String = TeamErrorCode.NOT_TEAM_ADMIN.message,
) : BusinessException(TeamErrorCode.NOT_TEAM_ADMIN, message)
