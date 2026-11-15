package com.duro.kukie.auth.application.port.out

interface PasswordResetCodeSender {
    fun send(email: String, code: String)
}
