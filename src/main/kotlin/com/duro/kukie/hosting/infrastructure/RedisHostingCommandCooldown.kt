package com.duro.kukie.hosting.infrastructure

import com.duro.kukie.hosting.application.port.out.HostingCommandCooldown
import com.duro.kukie.hosting.domain.HostingCommand
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.time.Duration

@Repository
class RedisHostingCommandCooldown(
    private val redisTemplate: StringRedisTemplate,
) : HostingCommandCooldown {

    override fun tryStart(command: HostingCommand): Boolean =
        redisTemplate.opsForValue()
            .setIfAbsent("hosting:command:${command.team}:${command.app}:${command.type}", command.commandId, COOLDOWN) == true

    companion object {
        // 배포기는 명령을 하나씩 처리한다. 한 앱의 연타가 다른 팀의 명령을 밀어내지 않게 한다
        private val COOLDOWN: Duration = Duration.ofMinutes(1)
    }
}
