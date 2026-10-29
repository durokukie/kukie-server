package com.duro.kukie.gitops.domain

import com.duro.kukie.gitops.exception.InvalidRepoUrlException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class RepoNameTest {

    @ParameterizedTest
    @ValueSource(
        strings = [
            "https://github.com/Acme/Kukie-Manifest",
            "https://github.com/acme/kukie-manifest.git",
            "https://github.com/acme/kukie-manifest/",
            "http://www.github.com/acme/kukie-manifest",
            "https://github.com/acme/kukie-manifest/tree/main/envs/dev",
            "git@github.com:acme/kukie-manifest.git",
            "git@github.com:Acme/Kukie-Manifest",
            "ssh://git@github.com/acme/kukie-manifest.git",
            "github.com/acme/kukie-manifest",
            "  acme/kukie-manifest  ",
        ],
    )
    fun `링크 모양이 달라도 같은 레포면 같은 소문자 owner_repo 로 맞춘다`(input: String) {
        // when
        val name = RepoName.parse(input)

        // then
        name shouldBe RepoName("acme", "kukie-manifest")
        name.fullName shouldBe "acme/kukie-manifest"
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "",
            "acme",
            "https://gitlab.com/acme/kukie-manifest",
            "git@gitlab.com:acme/kukie-manifest.git",
            "https://github.com/acme",
            "https://charts.example.com/acme/stable",
            "ftp://github.com/acme/kukie-manifest",
            "acme/..",
            "-acme/kukie-manifest",
            "acme/kukie manifest",
        ],
    )
    fun `GitHub 레포 링크가 아니면 거부한다`(input: String) {
        // when & then
        shouldThrow<InvalidRepoUrlException> { RepoName.parse(input) }
    }
}
