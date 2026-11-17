package com.duro.kukie.global.config.properties

import com.duro.kukie.hosting.application.GetHostingAppsService
import com.duro.kukie.hosting.application.HostingApps
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.util.UUID

class HostingPropertiesTest {

    @EnableConfigurationProperties(HostingProperties::class)
    class Config

    private val runner = ApplicationContextRunner().withUserConfiguration(Config::class.java)
    private val enabled = arrayOf("hosting.state-bucket=bucket", "hosting.commands-queue-url=https://queue")

    @Test
    fun `호스팅을 켠 서버는 GitHub App 을 주면 뜨고 설치 주소가 그 App 이다`() {
        runner.withPropertyValues(*enabled, "hosting.github-app=kukie-hosting-prod").run { context ->
            // given
            context.startupFailure.shouldBeNull()
            val hostingApps = mockk<HostingApps> { every { accountOf(any()) } returns null }

            // when
            val response = GetHostingAppsService(hostingApps, context.getBean(HostingProperties::class.java))(UUID.randomUUID())

            // then
            response.installUrl shouldBe "https://github.com/apps/kukie-hosting-prod/installations/new"
        }
    }

    @Test
    fun `호스팅을 켠 서버는 GitHub App 이 없거나 비어 있으면 뜨지 않는다`() {
        listOf(arrayOf(), arrayOf("hosting.github-app="), arrayOf("hosting.github-app= ")).forEach { app ->
            runner.withPropertyValues(*enabled, *app).run { context ->
                generateSequence(context.startupFailure.shouldNotBeNull()) { it.cause }.last().message shouldContain "HOSTING_GITHUB_APP"
            }
        }
    }

    @Test
    fun `호스팅을 끈 서버는 GitHub App 없이도 뜬다`() {
        listOf(arrayOf(), arrayOf("hosting.state-bucket=bucket"), arrayOf("hosting.commands-queue-url=https://queue")).forEach { hosting ->
            runner.withPropertyValues(*hosting).run { context ->
                context.startupFailure.shouldBeNull()
                context.getBean(HostingProperties::class.java).enabled shouldBe false
            }
        }
    }
}
