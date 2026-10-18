package com.duro.kukie.cluster.exception

import com.duro.kukie.global.exception.BusinessException

class AwsRoleNotAssumableException : BusinessException(ClusterErrorCode.AWS_ROLE_NOT_ASSUMABLE)
