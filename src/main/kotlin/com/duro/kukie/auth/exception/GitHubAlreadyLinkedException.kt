package com.duro.kukie.auth.exception

import com.duro.kukie.global.exception.BusinessException

class GitHubAlreadyLinkedException : BusinessException(AuthErrorCode.GITHUB_ALREADY_LINKED)
