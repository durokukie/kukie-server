package com.duro.kukie.cluster.infrastructure

import com.duro.kukie.cluster.application.HandleStackNoticeService
import com.duro.kukie.cluster.application.port.`in`.StackNoticeCommand
import com.duro.kukie.global.config.properties.AwsProperties
import com.duro.kukie.global.config.properties.ClusterAwsProperties
import com.duro.kukie.global.util.logger
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Component
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.Message
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.Duration

/**
 * 템플릿 v2 스택 알림 보관함(kukie-infra 의 kukie-access-notice SQS)을 길게 기다려 읽는다.
 * 보관함 주소(cluster.aws.notice-queue-url)가 있을 때만 뜬다 — 로컬·테스트에서는 돌지 않는다.
 */
@Component
@ConditionalOnProperty(prefix = "cluster.aws", name = ["notice-queue-url"])
class SqsStackNoticeListener(
    private val handleStackNoticeService: HandleStackNoticeService,
    private val clusterAwsProperties: ClusterAwsProperties,
    awsProperties: AwsProperties,
) : SmartLifecycle {

    private val log = logger()
    private val jsonMapper = JsonMapper.builder().build()
    private val sqsClient = SqsClient.builder()
        .region(Region.of(awsProperties.region))
        .overrideConfiguration { it.apiCallTimeout(API_CALL_TIMEOUT) }
        .build()

    @Volatile
    private var running = false
    private var worker: Thread? = null

    override fun start() {
        running = true
        worker = Thread.ofPlatform().name("stack-notice").daemon().start { poll() }
    }

    override fun stop() {
        running = false
        worker?.interrupt()
    }

    override fun isRunning(): Boolean = running

    private fun poll() {
        val queueUrl = requireNotNull(clusterAwsProperties.noticeQueueUrl)
        while (running) {
            try {
                val messages = sqsClient.receiveMessage {
                    it.queueUrl(queueUrl).maxNumberOfMessages(MAX_MESSAGES).waitTimeSeconds(WAIT_SECONDS)
                }.messages()
                messages.forEach { message -> handle(queueUrl, message) }
            } catch (exception: Exception) {
                if (!running) return
                log.warn("스택 알림 보관함을 읽지 못했습니다", exception)
                Thread.sleep(BACKOFF)
            }
        }
    }

    /** 끝낸 알림만 지운다. 실패하거나 다시 받기로 한 알림은 보관함이 잠시 뒤 다시 준다. */
    private fun handle(queueUrl: String, message: Message) {
        val done = try {
            parse(message.body())?.let { handleStackNoticeService(it) } ?: true
        } catch (exception: Exception) {
            log.warn("스택 알림을 처리하지 못했습니다: message={}", message.messageId(), exception)
            false
        }
        if (done) sqsClient.deleteMessage { it.queueUrl(queueUrl).receiptHandle(message.receiptHandle()) }
    }

    /** CloudFormation 요청 모양이 아니면 null — 버린다. */
    private fun parse(body: String): StackNoticeCommand? {
        val root = runCatching { jsonMapper.readTree(body) }.getOrNull() ?: return null
        val properties = root.path("ResourceProperties")
        return StackNoticeCommand(
            requestType = root.text("RequestType") ?: return null,
            responseUrl = root.text("ResponseURL") ?: return null,
            stackId = root.text("StackId") ?: return null,
            requestId = root.text("RequestId") ?: return null,
            logicalResourceId = root.text("LogicalResourceId") ?: return null,
            physicalResourceId = root.text("PhysicalResourceId"),
            externalId = properties.text("ExternalId"),
            roleArn = properties.text("RoleArn"),
        )
    }

    private fun JsonNode.text(field: String): String? = path(field).takeIf { it.isString }?.stringValue()

    companion object {
        private const val MAX_MESSAGES = 10
        private const val WAIT_SECONDS = 20
        private val API_CALL_TIMEOUT: Duration = Duration.ofSeconds(30)
        private val BACKOFF: Duration = Duration.ofSeconds(5)
    }
}
