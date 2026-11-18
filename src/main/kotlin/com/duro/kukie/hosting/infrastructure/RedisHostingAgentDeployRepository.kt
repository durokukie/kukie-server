package com.duro.kukie.hosting.infrastructure

import com.duro.kukie.hosting.domain.HostingAgentDeploy
import com.duro.kukie.hosting.domain.HostingAgentDeployRepository
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Repository
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

/** 배포 하나를 문자열 값 하나로 둔다(SET EX 한 번). 이름·계정에는 "|" 가 들어갈 수 없다. */
@Repository
class RedisHostingAgentDeployRepository(
    private val redisTemplate: StringRedisTemplate,
) : HostingAgentDeployRepository {

    override fun save(deploy: HostingAgentDeploy, ttl: Duration) {
        val value = listOf(
            deploy.userId, deploy.ownerId, deploy.login, deploy.team, deploy.app, deploy.bytes, deploy.createdAt,
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
            ownerId = f[1].toLong(),
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

    // 정하기와 먼저 정한 값 읽기를 한 번에 한다. 따로 하면 그 사이 표시가 풀렸을 때 정하지 않고도 정한 것으로 답한다
    override fun claimStart(id: String, commandId: String, ttl: Duration): String =
        redisTemplate.execute(SET_OR_GET, listOf(startKey(id)), commandId, ttl.toMillis().toString())
            ?: error("start claim of $id was not answered")

    override fun startClaimAge(id: String, ttl: Duration): Duration? {
        val left = redisTemplate.getExpire(startKey(id), TimeUnit.MILLISECONDS)
        return if (left < 0) null else ttl.minusMillis(left)
    }

    override fun releaseStart(id: String, commandId: String) {
        redisTemplate.execute(DELETE_IF, listOf(startKey(id)), commandId)
    }

    private fun deployKey(id: String) = "hosting:agent:deploy:$id"

    private fun startKey(id: String) = "hosting:agent:start:$id"

    private fun activeKey(team: String) = "hosting:agent:active:$team"

    companion object {
        private val SET_OR_GET = RedisScript.of<String>(
            "if redis.call('SET', KEYS[1], ARGV[1], 'NX', 'PX', ARGV[2]) then return ARGV[1] end return redis.call('GET', KEYS[1])",
            String::class.java,
        )
        private val DELETE_IF = RedisScript.of<Long>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) end return 0",
            Long::class.java,
        )
    }
}
