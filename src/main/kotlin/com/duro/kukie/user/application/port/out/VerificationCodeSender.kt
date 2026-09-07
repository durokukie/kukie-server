package com.duro.kukie.user.application.port.out

interface VerificationCodeSender {
    fun send(email: String, code: String)
}