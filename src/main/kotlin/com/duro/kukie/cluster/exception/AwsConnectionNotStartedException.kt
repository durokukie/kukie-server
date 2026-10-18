package com.duro.kukie.cluster.exception

import com.duro.kukie.global.exception.BusinessException

class AwsConnectionNotStartedException : BusinessException(ClusterErrorCode.AWS_CONNECTION_NOT_STARTED)
