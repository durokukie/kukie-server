package com.duro.kukie.global.mail

import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Component

@Component
class MailClient(
    private val mailSender: JavaMailSender,
) {

    fun send(to: String, subject: String, htmlBody: String) {
        val message = mailSender.createMimeMessage()
        val helper = MimeMessageHelper(message, true, "UTF-8")

        helper.apply {
            setTo(to)
            setSubject(subject)
            setText(htmlBody, true)
        }

        mailSender.send(message)
    }
}
