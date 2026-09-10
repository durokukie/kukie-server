package com.duro.kukie.team

import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.team.domain.TeamMembershipRepository
import com.duro.kukie.team.domain.TeamPermission
import com.duro.kukie.team.domain.TeamRepository
import com.duro.kukie.team.exception.AdminRequiredException
import com.duro.kukie.user.UserFixture
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * "팀에 관리자 최소 1명" (제품기획서 02 §3) 은 세기만 해서는 지켜지지 않는다. 관리자 A·B 뿐인 팀에서
 * 서로를 동시에 제거하면 두 트랜잭션이 모두 2를 읽고 통과해 **관리자 0명인 팀**이 남는다. 그러면 모든
 * 관리 API 가 403 이라 팀을 고칠 수도 지울 수도 없다 — 되돌릴 방법이 없는 상태다 (자동 리뷰 지적).
 *
 * MockMvc 로 두 요청을 동시에 보내는 방법으로는 이 창이 너무 좁아 재현되지 않는다. 그래서 트랜잭션
 * 둘을 직접 열어 겹치는 순간을 만든다.
 */
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
        // given — 관리자 둘인 팀
        val team = teamRepository.save(TeamFixture.team())
        val first = userRepository.save(UserFixture.user(email = "first@example.com"))
        val second = userRepository.save(UserFixture.user(email = "second@example.com"))
        teamMembershipRepository.save(TeamFixture.membership(team.id, first.id))
        val secondMembership = teamMembershipRepository.save(TeamFixture.membership(team.id, second.id))

        val transaction = TransactionTemplate(transactionManager)
        val removing = CountDownLatch(1)

        // when — 한쪽이 관리자 하나를 지우는 트랜잭션을 연 채로
        val remover = Thread {
            transaction.execute {
                teamPermission.requireNotLastAdmin(team.id)
                teamMembershipRepository.delete(secondMembership)
                teamMembershipRepository.flush()
                removing.countDown()
                // 다른 쪽이 같은 검사를 시작할 시간을 준다. 잠금이 없으면 그쪽은 아직 둘로 본다.
                Thread.sleep(500)
                null
            }
        }
        remover.start()
        removing.await(5, TimeUnit.SECONDS) shouldBe true

        // 다른 쪽이 같은 검사를 한다 — 앞 트랜잭션이 끝날 때까지 기다렸다가 남은 하나를 본다
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

        // then — 두 번째 제거는 막힌다. 막지 못하면 관리자 0명인 팀이 된다.
        rejected shouldBe true
        teamMembershipRepository.findAllByTeamId(team.id).count { it.role.isAdmin } shouldBe 1
    }
}
