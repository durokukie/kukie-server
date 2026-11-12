package com.duro.kukie.hosting.infrastructure

import com.duro.kukie.global.config.properties.AwsProperties
import com.duro.kukie.global.config.properties.HostingProperties
import com.duro.kukie.global.util.logger
import com.duro.kukie.hosting.application.port.out.HostingCommandQueue
import com.duro.kukie.hosting.domain.HostingCommand
import com.duro.kukie.hosting.exception.HostingUnavailableException
import org.springframework.stereotype.Component
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsClient
import tools.jackson.databind.ObjectMapper
import java.time.Duration

@Component
class SqsHostingCommandQueue(
    private val hostingProperties: HostingProperties,
    private val objectMapper: ObjectMapper,
    awsProperties: AwsProperties,
) : HostingCommandQueue {

    private val log = logger()

    private val sqsClient = SqsClient.builder()
        .region(Region.of(awsProperties.region))
        .overrideConfiguration { it.apiCallTimeout(API_CALL_TIMEOUT) }
        .build()

    override fun send(command: HostingCommand) {
        try {
            sqsClient.sendMessage { it.queueUrl(hostingProperties.commandsQueueUrl).messageBody(objectMapper.writeValueAsString(command)) }
        } catch (exception: SdkException) {
            log.warn("호스팅 명령을 보내지 못했습니다: type={}, app={}/{}", command.type, command.team, command.target, exception)
            throw HostingUnavailableException()
        }
    }

    companion object {
        private val API_CALL_TIMEOUT: Duration = Duration.ofSeconds(10)
    }
}
