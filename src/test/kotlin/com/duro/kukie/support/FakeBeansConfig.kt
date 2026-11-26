package com.duro.kukie.support

import com.duro.kukie.report.application.port.out.ReportTaskRunner
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration(proxyBeanMethods = false)
class FakeBeansConfig {

    @Bean
    @Primary
    fun fakeVerificationCodeSender(): FakeVerificationCodeSender = FakeVerificationCodeSender()

    @Bean
    @Primary
    fun fakePasswordResetCodeSender(): FakePasswordResetCodeSender = FakePasswordResetCodeSender()

    @Bean
    @Primary
    fun fakeOAuthClient(): FakeOAuthClient = FakeOAuthClient()

    @Bean
    @Primary
    fun fakeTeamInvitationSender(): FakeTeamInvitationSender = FakeTeamInvitationSender()

    @Bean
    @Primary
    fun fakeHostResolver(): FakeHostResolver = FakeHostResolver()

    @Bean
    @Primary
    fun fakeEksAccessGateway(): FakeEksAccessGateway = FakeEksAccessGateway()

    @Bean
    @Primary
    fun fakeGitHubAppGateway(): FakeGitHubAppGateway = FakeGitHubAppGateway()

    @Bean
    @Primary
    fun fakeStackNoticeResponder(): FakeStackNoticeResponder = FakeStackNoticeResponder()

    @Bean
    @Primary
    fun fakeHuggingFaceClient(): FakeHuggingFaceClient = FakeHuggingFaceClient()

    @Bean
    @Primary
    fun fakeHostingStateStore(): FakeHostingStateStore = FakeHostingStateStore()

    @Bean
    @Primary
    fun fakeHostingCommandQueue(): FakeHostingCommandQueue = FakeHostingCommandQueue()

    @Bean
    @Primary
    fun fakeHostingUploadStore(): FakeHostingUploadStore = FakeHostingUploadStore()

    @Bean
    @Primary
    fun fakeGitHubAccountAge(): FakeGitHubAccountAge = FakeGitHubAccountAge()

    @Bean
    @Primary
    fun fakeHostingFailureAnalyzer(): FakeHostingFailureAnalyzer = FakeHostingFailureAnalyzer()

    @Bean
    @Primary
    fun fakeReportOutlineClient(): FakeReportOutlineClient = FakeReportOutlineClient()

    @Bean
    @Primary
    fun fakeClusterMetricsReader(): FakeClusterMetricsReader = FakeClusterMetricsReader()

    /** 커밋 직후 같은 스레드에서 만들어, 요청이 돌아오면 보고서가 완성돼 있다. */
    @Bean
    @Primary
    fun inlineReportTaskRunner(): ReportTaskRunner = ReportTaskRunner { task -> task() }
}
