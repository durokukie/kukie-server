package com.duro.kukie.user.application.port

interface VerificationCodeSender {
    fun send(email: String, code: String)
}