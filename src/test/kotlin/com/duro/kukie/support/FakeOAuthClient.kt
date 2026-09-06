package com.duro.kukie.support

import com.duro.kukie.auth.application.port.out.OAuthClient
import com.duro.kukie.auth.application.port.out.OAuthProfile
import com.duro.kukie.auth.domain.OAuthProvider
import com.duro.kukie.auth.exception.OAuthLogInFailedException

class FakeOAuthClient : OAuthClient, Resettable {

    var profile = DEFAULT_PROFILE
    var shouldFail = false

    override fun fetchProfile(
        provider: OAuthProvider,
        code: String,
        redirectUri: String,
        codeVerifier: String?,
    ): OAuthProfile {
        if (shouldFail) throw OAuthLogInFailedException()
        return profile
    }

    override fun clear() {
        profile = DEFAULT_PROFILE
        shouldFail = false
    }

    companion object {
        val DEFAULT_PROFILE = OAuthProfile(email = "test@example.com", name = "테스트")
    }
}
