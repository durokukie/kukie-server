package com.duro.kukie.serving

import com.duro.kukie.global.domain.Email
import com.duro.kukie.global.util.utcNow
import com.duro.kukie.serving.domain.ServingAccess
import com.duro.kukie.serving.domain.ServingAccessStatus
import com.duro.kukie.serving.exception.ServingAccessException
import com.duro.kukie.serving.exception.ServingErrorCode
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID

class ServingAccessTest {
    @Test
    fun `초대 유효 기간은 활성 권한의 유효 기간이 아니다`() {
        // given
        val now = utcNow()
        val user = UUID.randomUUID()
        val access = ServingAccess(UUID.randomUUID(), Email("a@example.com"), UUID.randomUUID(), now.plusDays(7))

        // when
        access.accept(user, Email("a@example.com"), now)

        // then
        access.statusAt(now.plusDays(8)) shouldBe ServingAccessStatus.ACTIVE
    }

    @Test
    fun `만료 시각과 같으면 초대를 수락할 수 없다`() {
        // given
        val now = utcNow()
        val access = ServingAccess(UUID.randomUUID(), Email("a@example.com"), UUID.randomUUID(), now)

        // when then
        shouldThrow<ServingAccessException> { access.accept(UUID.randomUUID(), Email("a@example.com"), now) }
            .errorCode shouldBe ServingErrorCode.SERVING_INVITATION_EXPIRED
    }

    @Test
    fun `철회된 권한은 재수락할 수 없다`() {
        // given
        val now = utcNow()
        val access = ServingAccess(UUID.randomUUID(), Email("a@example.com"), UUID.randomUUID(), now.plusDays(7))
        access.revoke()

        // when then
        shouldThrow<ServingAccessException> { access.accept(UUID.randomUUID(), Email("a@example.com"), now) }
            .errorCode shouldBe ServingErrorCode.SERVING_ACCESS_NOT_ACTIVE
        access.status shouldBe ServingAccessStatus.REVOKED
    }
}
