package com.duro.kukie.cluster.exception

import com.duro.kukie.global.exception.BusinessException

class AwsRegionNotSupportedException : BusinessException(ClusterErrorCode.AWS_REGION_NOT_SUPPORTED)
