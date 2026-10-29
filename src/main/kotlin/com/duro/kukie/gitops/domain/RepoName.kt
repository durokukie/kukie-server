package com.duro.kukie.gitops.domain

import com.duro.kukie.gitops.exception.InvalidRepoUrlException

/** 정규화한 GitHub 레포 이름(소문자 `owner/repo`). 링크 모양이 달라도 같은 레포면 같은 값이다. */
data class RepoName(
    val owner: String,
    val name: String,
) {
    val fullName: String
        get() = "$owner/$name"

    override fun toString(): String = fullName

    companion object {
        private const val HOST = "github.com"
        private val OWNER = Regex("^[a-z0-9](?:[a-z0-9-]{0,38})$")
        private val NAME = Regex("^[a-z0-9._-]{1,100}$")
        private val SCP_LIKE = Regex("^[^/@]+@([^:/]+):(.+)$")
        private val SCHEME = Regex("^[a-z][a-z0-9+.-]*://")

        /**
         * `https://github.com/o/r(.git)`, `ssh://git@github.com/o/r.git`, `git@github.com:o/r.git`, `github.com/o/r`, `o/r`
         * 를 받는다. 브라우저 주소처럼 뒤에 경로(`/tree/main/…`)가 붙어 있으면 앞의 두 칸만 본다.
         */
        fun parse(input: String): RepoName {
            val text = input.trim().lowercase()
            val path = SCP_LIKE.matchEntire(text)?.let { match -> pathOn(match.groupValues[1], match.groupValues[2]) }
                ?: if (SCHEME.containsMatchIn(text)) fromUrl(text) else fromBare(text)
            val segments = path.trim('/').split('/').filter { it.isNotEmpty() }
            if (segments.size < 2) throw InvalidRepoUrlException()

            val owner = segments[0]
            val name = segments[1].removeSuffix(".git")
            if (!OWNER.matches(owner) || !NAME.matches(name) || name == "." || name == "..") throw InvalidRepoUrlException()

            return RepoName(owner, name)
        }

        private fun fromUrl(text: String): String {
            val uri = runCatching { java.net.URI(text) }.getOrNull() ?: throw InvalidRepoUrlException()
            if (uri.scheme !in setOf("https", "http", "ssh", "git")) throw InvalidRepoUrlException()
            return pathOn(uri.host ?: throw InvalidRepoUrlException(), uri.path.orEmpty())
        }

        private fun fromBare(text: String): String {
            val first = text.substringBefore('/')
            return if ('.' in first) pathOn(first, text.substringAfter('/', "")) else text
        }

        private fun pathOn(host: String, path: String): String {
            if (host.removePrefix("www.") != HOST) throw InvalidRepoUrlException()
            return path
        }
    }
}
