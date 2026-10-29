package com.duro.kukie.gitops.exception

import com.duro.kukie.global.exception.BusinessException

class ManifestRepoNotConnectedException : BusinessException(GitOpsErrorCode.MANIFEST_REPO_NOT_CONNECTED)
