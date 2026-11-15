package com.duro.kukie.auth.exception

import com.duro.kukie.global.exception.BusinessException

class GitHubLinkFailedException : BusinessException(AuthErrorCode.GITHUB_LINK_FAILED)
