package com.duro.kukie.global.mail

import com.duro.kukie.global.config.properties.AwsSesProperties
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.sesv2.SesV2Client
import software.amazon.awssdk.services.sesv2.model.Content

@Component
class MailClient(
    private val sesV2Client: SesV2Client,
    private val awsSesProperties: AwsSesProperties,
) {

    fun send(to: String, subject: String, htmlBody: String) {
        sesV2Client.sendEmail { request ->
            request
                .fromEmailAddress(awsSesProperties.from)
                .destination { it.toAddresses(to) }
                .content { content ->
                    content.simple { message ->
                        message
                            .subject(utf8(subject))
                            .body { it.html(utf8(htmlBody)) }
                    }
                }
        }
    }

    private fun utf8(data: String): Content = Content.builder().data(data).charset("UTF-8").build()
}
