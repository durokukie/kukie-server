package com.duro.kukie.auth.presentation

import com.duro.kukie.auth.domain.OAuthProvider
import org.springframework.core.convert.converter.Converter
import org.springframework.stereotype.Component

@Component
class OAuthProviderConverter : Converter<String, OAuthProvider> {

    override fun convert(source: String): OAuthProvider = OAuthProvider.from(source)
}
