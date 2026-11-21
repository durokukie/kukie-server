package com.duro.kukie.user

import com.duro.kukie.global.util.utcNow
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.user.domain.RegisteredUserCounts
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class RegisteredUserCountsIntegrationTest : IntegrationTest() {
    @Test
    fun `탈퇴와 같은 이메일 재가입을 현재 회원 수와 누적 가입 수에 반영한다`() {
        userRepository.registeredCounts() shouldBe RegisteredUserCounts(0, 0)
        val user = userRepository.save(UserFixture.user())
        userRepository.registeredCounts() shouldBe RegisteredUserCounts(1, 1)

        user.anonymize(utcNow())
        userRepository.save(user)
        userRepository.registeredCounts() shouldBe RegisteredUserCounts(0, 1)

        userRepository.save(UserFixture.oAuthUser())
        userRepository.registeredCounts() shouldBe RegisteredUserCounts(1, 2)
    }
}
