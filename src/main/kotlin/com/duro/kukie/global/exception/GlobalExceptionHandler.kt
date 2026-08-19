package com.duro.kukie.global.exception

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(e: BusinessException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(e.errorCode.status)
            .body(ErrorResponse(e.errorCode.code, e.errorCode.message))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val errorCode = GlobalErrorCode.BAD_REQUEST
        val message = e.bindingResult.fieldErrors.firstOrNull()?.defaultMessage ?: errorCode.message
        return ResponseEntity
            .status(errorCode.status)
            .body(ErrorResponse(errorCode.code, message))
    }
}