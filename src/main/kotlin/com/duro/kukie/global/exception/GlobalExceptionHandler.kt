package com.duro.kukie.global.exception

import com.duro.kukie.global.util.logger
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = logger()

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val errorCode = GlobalErrorCode.BAD_REQUEST
        val message = e.bindingResult.fieldErrors.firstOrNull()?.defaultMessage ?: errorCode.message

        return ResponseEntity
            .status(errorCode.status)
            .body(ErrorResponse(errorCode.code, message))
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatchException(e: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> {
        val errorCode = GlobalErrorCode.BAD_REQUEST

        return ResponseEntity
            .status(errorCode.status)
            .body(ErrorResponse(errorCode.code, errorCode.message))
    }

    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(e: BusinessException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(e.errorCode.status)
            .body(ErrorResponse(e.errorCode.code, e.errorCode.message))
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ErrorResponse> {
        val errorCode = GlobalErrorCode.INTERNAL_SERVER_ERROR
        log.error(e.message, e)

        return ResponseEntity
            .status(errorCode.status)
            .body(ErrorResponse(errorCode.code, errorCode.message))
    }
}