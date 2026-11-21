package com.duro.kukie.user

import com.duro.kukie.global.util.utcNow
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.user.domain.RegisteredUserCounts
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class RegisteredUserCountsIntegrationTest : IntegrationTest() {
    @Test
    fun `탈퇴와 같은 이메일 재가입을 현재 회원 수와 누적 가입 수에 반영한다`() {
        // given
        val empty = userRepository.registeredCounts()

        // when
        val user = userRepository.save(UserFixture.user())
        val joined = userRepository.registeredCounts()

        user.anonymize(utcNow())
        userRepository.save(user)
        val withdrawn = userRepository.registeredCounts()

        userRepository.save(UserFixture.oAuthUser())
        val rejoined = userRepository.registeredCounts()

        // then
        empty shouldBe RegisteredUserCounts(0, 0)
        joined shouldBe RegisteredUserCounts(1, 1)
        withdrawn shouldBe RegisteredUserCounts(0, 1)
        rejoined shouldBe RegisteredUserCounts(1, 2)
    }
}
