package com.duro.kukie.actionplan.exception

import com.duro.kukie.global.exception.BusinessException

class ActionPlanNotFoundException : BusinessException(ActionPlanErrorCode.NOT_FOUND)
