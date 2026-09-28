package com.duro.kukie.team

import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.team.application.TeamPermission
import com.duro.kukie.team.domain.TeamMembershipRepository
import com.duro.kukie.team.domain.TeamRepository
import com.duro.kukie.team.domain.TeamRole
import com.duro.kukie.team.exception.AdminRequiredException
import com.duro.kukie.user.UserFixture
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** MockMvc 동시 요청으로는 경합이 재현되지 않아 트랜잭션 둘을 직접 열어 겹치게 한다. */
class TeamPermissionConcurrencyTest : IntegrationTest() {

    @Autowired
    private lateinit var teamRepository: TeamRepository

    @Autowired
    private lateinit var teamMembershipRepository: TeamMembershipRepository

    @Autowired
    private lateinit var teamPermission: TeamPermission

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Test
    fun `한쪽이 관리자를 지우는 중이면 다른 쪽은 마지막 관리자로 본다`() {
        // given
        val team = teamRepository.save(TeamFixture.team())
        val first = userRepository.save(UserFixture.user(email = "first@example.com"))
        val second = userRepository.save(UserFixture.user(email = "second@example.com"))
        teamMembershipRepository.save(TeamFixture.membership(team.id, first.id))
        val secondMembership = teamMembershipRepository.save(TeamFixture.membership(team.id, second.id))

        val transaction = TransactionTemplate(transactionManager)
        val removing = CountDownLatch(1)

        // when
        val remover = Thread {
            transaction.execute {
                teamPermission.requireNotLastAdmin(team.id)
                teamMembershipRepository.delete(secondMembership)
                teamMembershipRepository.flush()
                removing.countDown()
                // 다른 쪽이 같은 검사를 시작할 시간을 준다
                Thread.sleep(500)
                null
            }
        }
        remover.start()
        removing.await(5, TimeUnit.SECONDS) shouldBe true

        // 다른 쪽이 같은 검사를 한다
        var rejected = false
        transaction.execute {
            try {
                teamPermission.requireNotLastAdmin(team.id)
            } catch (exception: AdminRequiredException) {
                rejected = true
            }
            null
        }
        remover.join()

        // then
        rejected shouldBe true
        teamMembershipRepository.findAllByTeamId(team.id).count { it.role == TeamRole.ADMIN } shouldBe 1
    }
}
