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

    /** 요청 본문 DTO 의 Bean Validation(`@NotBlank`, `@Email` 등) 위반. */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val errorCode = GlobalErrorCode.BAD_REQUEST
        val message = e.bindingResult.fieldErrors.firstOrNull()?.defaultMessage ?: errorCode.message

        return ResponseEntity
            .status(errorCode.status)
            .body(ErrorResponse(errorCode.code, message))
    }

    /** 경로 변수·쿼리 파라미터 타입 변환 실패 (예: 형식이 틀린 UUID, 없는 OAuth 제공자). */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatchException(e: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> {
        val errorCode = GlobalErrorCode.BAD_REQUEST

        return ResponseEntity
            .status(errorCode.status)
            .body(ErrorResponse(errorCode.code, errorCode.message))
    }

    /** 본문 역직렬화 실패 (없는 enum 값, 빠진 필드, 깨진 JSON). */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleNotReadableException(e: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> {
        val errorCode = GlobalErrorCode.BAD_REQUEST

        return ResponseEntity
            .status(errorCode.status)
            .body(ErrorResponse(errorCode.code, errorCode.message))
    }

    /** 매핑된 핸들러가 없는 경로. */
    @ExceptionHandler(NoResourceFoundException::class, NoHandlerFoundException::class)
    fun handleNotFoundException(e: Exception): ResponseEntity<ErrorResponse> {
        val errorCode = GlobalErrorCode.NOT_FOUND

        return ResponseEntity
            .status(errorCode.status)
            .body(ErrorResponse(errorCode.code, errorCode.message))
    }

    /** 경로는 있지만 지원하지 않는 HTTP 메서드. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupportedException(e: HttpRequestMethodNotSupportedException): ResponseEntity<ErrorResponse> {
        val errorCode = GlobalErrorCode.METHOD_NOT_ALLOWED

        return ResponseEntity
            .status(errorCode.status)
            .body(ErrorResponse(errorCode.code, errorCode.message))
    }

    /** 각 기능의 `ErrorCode` 로 정의된 비즈니스 예외. */
    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(e: BusinessException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(e.errorCode.status)
            .body(ErrorResponse(e.errorCode.code, e.errorCode.message))
    }

    /** 위에서 처리하지 못한 모든 예외 — 500 으로 응답하고 로그를 남긴다. */
    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ErrorResponse> {
        val errorCode = GlobalErrorCode.INTERNAL_SERVER_ERROR
        log.error(e.message, e)

        return ResponseEntity
            .status(errorCode.status)
            .body(ErrorResponse(errorCode.code, errorCode.message))
    }
}
