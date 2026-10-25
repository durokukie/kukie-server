package com.duro.kukie.cluster.exception

import com.duro.kukie.global.exception.BusinessException

class AwsAccountMismatchException : BusinessException(ClusterErrorCode.AWS_ACCOUNT_MISMATCH)
