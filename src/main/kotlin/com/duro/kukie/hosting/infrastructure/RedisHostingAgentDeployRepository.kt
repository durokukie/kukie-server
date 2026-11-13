package com.duro.kukie.hosting.infrastructure

import com.duro.kukie.hosting.domain.HostingAgentDeploy
import com.duro.kukie.hosting.domain.HostingAgentDeployRepository
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Repository
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** 배포 하나를 문자열 값 하나로 둔다(SET EX 한 번). 이름·계정에는 "|" 가 들어갈 수 없다. */
@Repository
class RedisHostingAgentDeployRepository(
    private val redisTemplate: StringRedisTemplate,
) : HostingAgentDeployRepository {

    override fun save(deploy: HostingAgentDeploy, ttl: Duration) {
        val value = listOf(
            deploy.userId, deploy.githubId, deploy.login, deploy.team, deploy.app, deploy.bytes, deploy.createdAt,
            deploy.commandId, deploy.startedAt, deploy.deploymentId, deploy.generation,
        ).joinToString("|") { it?.toString().orEmpty() }
        redisTemplate.opsForValue().set(deployKey(deploy.id), value, ttl)
    }

    override fun find(id: String): HostingAgentDeploy? {
        val f = redisTemplate.opsForValue().get(deployKey(id))?.split("|") ?: return null
        fun opt(i: Int) = f[i].ifEmpty { null }
        return HostingAgentDeploy(
            id = id,
            userId = UUID.fromString(f[0]),
            githubId = f[1].toLong(),
            login = f[2],
            team = f[3],
            app = f[4],
            bytes = f[5].toLong(),
            createdAt = Instant.parse(f[6]),
            commandId = opt(7),
            startedAt = opt(8)?.let(Instant::parse),
            deploymentId = opt(9),
            generation = opt(10),
        )
    }

    override fun activeOf(team: String): String? = redisTemplate.opsForValue().get(activeKey(team))

    override fun claimActive(team: String, id: String, ttl: Duration): Boolean =
        redisTemplate.opsForValue().setIfAbsent(activeKey(team), id, ttl) == true || activeOf(team) == id

    override fun clearActive(team: String, id: String) {
        redisTemplate.execute(DELETE_IF, listOf(activeKey(team)), id)
    }

    override fun claimStart(id: String, commandId: String, ttl: Duration): String {
        if (redisTemplate.opsForValue().setIfAbsent(startKey(id), commandId, ttl) == true) return commandId
        return redisTemplate.opsForValue().get(startKey(id)) ?: commandId
    }

    override fun releaseStart(id: String) {
        redisTemplate.delete(startKey(id))
    }

    private fun deployKey(id: String) = "hosting:agent:deploy:$id"

    private fun startKey(id: String) = "hosting:agent:start:$id"

    private fun activeKey(team: String) = "hosting:agent:active:$team"

    companion object {
        private val DELETE_IF = RedisScript.of<Long>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) end return 0",
            Long::class.java,
        )
    }
}
