package com.duro.kukie.cluster.exception

import com.duro.kukie.global.exception.BusinessException

class GpuAddonNotInstalledException : BusinessException(ClusterErrorCode.GPU_ADDON_NOT_INSTALLED)
