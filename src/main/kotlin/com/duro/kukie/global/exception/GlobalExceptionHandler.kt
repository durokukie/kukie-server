package com.duro.kukie.global.exception

import com.duro.kukie.global.util.logger
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.servlet.resource.NoResourceFoundException

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

    /**
     * 본문을 읽지 못한 요청. enum 에 없는 값(`{"role":"OWNER"}`), 빠진 필수 필드, 깨진 JSON 이 여기로 온다.
     *
     * Jackson 역직렬화는 Bean Validation 보다 먼저라 `@NotNull` 이 잡을 기회가 없다. 이 핸들러가 없으면
     * 클라이언트의 입력 실수가 catch-all 로 떨어져 500 으로 나간다.
     */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleNotReadableException(e: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> {
        val errorCode = GlobalErrorCode.BAD_REQUEST

        return ResponseEntity
            .status(errorCode.status)
            .body(ErrorResponse(errorCode.code, errorCode.message))
    }

    @ExceptionHandler(NoResourceFoundException::class, NoHandlerFoundException::class)
    fun handleNotFoundException(e: Exception): ResponseEntity<ErrorResponse> {
        val errorCode = GlobalErrorCode.NOT_FOUND

        return ResponseEntity
            .status(errorCode.status)
            .body(ErrorResponse(errorCode.code, errorCode.message))
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupportedException(e: HttpRequestMethodNotSupportedException): ResponseEntity<ErrorResponse> {
        val errorCode = GlobalErrorCode.METHOD_NOT_ALLOWED

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