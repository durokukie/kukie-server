package com.duro.kukie.team.presentation

import com.duro.kukie.auth.exception.UnauthorizedException
import com.duro.kukie.global.exception.BusinessException
import com.duro.kukie.global.exception.GlobalErrorCode
import com.duro.kukie.global.security.AuthenticationInterceptor
import com.duro.kukie.team.domain.TeamMembershipRepository
import com.duro.kukie.team.domain.findByTeamIdAndUserIdOrThrow
import com.duro.kukie.team.exception.InsufficientTeamRoleException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.HandlerMapping
import java.util.UUID

@Component
class TeamRoleInterceptor(
    private val teamMembershipRepository: TeamMembershipRepository,
) : HandlerInterceptor {

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        if (handler !is HandlerMethod) {
            return true
        }
        val required = handler.getMethodAnnotation(TeamRoleRequired::class.java) ?: return true

        val teamId = request.teamId()
        val userId = request.getAttribute(AuthenticationInterceptor.AUTHENTICATED_USER_ID) as? UUID
            ?: throw UnauthorizedException()

        val teamMember = teamMembershipRepository.findByTeamIdAndUserIdOrThrow(teamId, userId)
        if (teamMember.role.level < required.role.level)
            throw InsufficientTeamRoleException()

        return true
    }

    /** 인터셉터 시점엔 경로 변수가 아직 문자열이다. 형식이 틀리면 핸들러 인자 변환이 냈을 것과 같은 400 을 낸다. */
    private fun HttpServletRequest.teamId(): UUID {
        val variables = getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) as? Map<*, *>
        val raw = variables?.get(TEAM_ID) as? String
            ?: error("@TeamRoleRequired 핸들러에는 {$TEAM_ID} 경로 변수가 있어야 합니다: $requestURI")

        return runCatching { UUID.fromString(raw) }
            .getOrElse { throw BusinessException(GlobalErrorCode.BAD_REQUEST) }
    }

    companion object {
        const val TEAM_ID = "teamId"
    }
}
