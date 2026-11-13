package com.duro.kukie.hosting.infrastructure

import com.duro.kukie.hosting.domain.HostingAgentLogin
import com.duro.kukie.hosting.domain.HostingAgentLoginRepository
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Repository
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** 로그인 하나를 문자열 값 하나("상태|코드|요청 시각|회원")로 둔다. 쓰기는 SET EX 한 번이라 TTL 없이 남는 순간이 없다. */
@Repository
class RedisHostingAgentLoginRepository(
    private val redisTemplate: StringRedisTemplate,
) : HostingAgentLoginRepository {

    override fun create(pollTokenHash: String, login: HostingAgentLogin, ttl: Duration): Boolean {
        if (redisTemplate.opsForValue().setIfAbsent(codeKey(login.code), pollTokenHash, ttl) != true) return false
        redisTemplate.opsForValue().set(loginKey(pollTokenHash), encode(login), ttl)
        return true
    }

    override fun findByPollTokenHash(pollTokenHash: String): HostingAgentLogin? =
        redisTemplate.opsForValue().get(loginKey(pollTokenHash))?.let(::decode)

    override fun findPollTokenHashByCode(code: String): String? = redisTemplate.opsForValue().get(codeKey(code))

    override fun transition(pollTokenHash: String, from: HostingAgentLogin.Status, to: HostingAgentLogin, ttl: Duration): Boolean =
        redisTemplate.execute(
            TRANSITION,
            listOf(loginKey(pollTokenHash), codeKey(to.code)),
            "${from.name}|",
            encode(to),
            ttl.seconds.toString(),
        ) == 1L

    private fun encode(login: HostingAgentLogin) =
        listOf(login.status.name, login.code, login.requestedAt.toString(), login.userId?.toString().orEmpty()).joinToString("|")

    private fun decode(value: String): HostingAgentLogin {
        val (status, code, requestedAt, userId) = value.split("|")
        return HostingAgentLogin(code, HostingAgentLogin.Status.valueOf(status), Instant.parse(requestedAt), userId.ifEmpty { null }?.let(UUID::fromString))
    }

    private fun loginKey(pollTokenHash: String) = "hosting:agent:login:$pollTokenHash"

    private fun codeKey(code: String) = "hosting:agent:code:$code"

    companion object {
        private val TRANSITION = RedisScript.of<Long>(
            """
            local value = redis.call('GET', KEYS[1])
            if not value or string.sub(value, 1, string.len(ARGV[1])) ~= ARGV[1] then return 0 end
            redis.call('SET', KEYS[1], ARGV[2], 'EX', tonumber(ARGV[3]))
            redis.call('EXPIRE', KEYS[2], tonumber(ARGV[3]))
            return 1
            """.trimIndent(),
            Long::class.java,
        )
    }
}
