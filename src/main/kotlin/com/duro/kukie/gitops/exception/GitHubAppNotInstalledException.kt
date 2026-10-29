package com.duro.kukie.gitops.exception

import com.duro.kukie.global.exception.BusinessException

class GitHubAppNotInstalledException : BusinessException(GitOpsErrorCode.GITHUB_APP_NOT_INSTALLED)
