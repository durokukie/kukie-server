package com.duro.kukie.global.config

import com.duro.kukie.global.docs.ApiErrorResponses
import com.duro.kukie.global.exception.ErrorCode
import com.duro.kukie.global.exception.ErrorResponse
import com.duro.kukie.global.security.AuthUser
import com.duro.kukie.global.security.Authenticated
import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.examples.Example
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springdoc.core.customizers.OperationCustomizer
import org.springdoc.core.utils.SpringDocUtils
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.web.method.HandlerMethod
import kotlin.reflect.full.createInstance

@Configuration
class SwaggerConfig {

    init {
        SpringDocUtils.getConfig().addAnnotationsToIgnore(AuthUser::class.java)
    }

    @Bean
    fun openApi(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("Kukie API")
                    .description("Kukie 서버 API 문서")
                    .version("v1"),
            )
            .components(
                Components()
                    .addSecuritySchemes(
                        SECURITY_SCHEME_NAME,
                        SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("Bearer")
                            .bearerFormat("JWT"),
                    )
                    .addSchemas(ERROR_RESPONSE_SCHEMA_NAME, errorResponseSchema()),
            )

    @Bean
    fun authenticatedOperationCustomizer(): OperationCustomizer =
        OperationCustomizer { operation, handlerMethod ->
            if (handlerMethod.requiresAuthentication()) {
                operation.addSecurityItem(SecurityRequirement().addList(SECURITY_SCHEME_NAME))
            }
            operation
        }

    @Bean
    fun apiErrorResponsesCustomizer(): OperationCustomizer =
        OperationCustomizer { operation, handlerMethod ->
            handlerMethod.errorCodes()
                .groupBy { it.status }
                .forEach { (status, errorCodes) ->
                    operation.responses.addApiResponse(status.value().toString(), errorCodes.toApiResponse())
                }
            operation
        }

    private fun HandlerMethod.requiresAuthentication(): Boolean =
        hasMethodAnnotation(Authenticated::class.java) ||
            AnnotatedElementUtils.hasAnnotation(beanType, Authenticated::class.java)

    private fun HandlerMethod.errorCodes(): List<ErrorCode> =
        AnnotatedElementUtils.findMergedAnnotation(method, ApiErrorResponses::class.java)
            ?.value
            ?.map { it.createInstance().errorCode }
            .orEmpty()

    private fun List<ErrorCode>.toApiResponse(): ApiResponse {
        val mediaType = MediaType().schema(Schema<Any>().`$ref`("#/components/schemas/$ERROR_RESPONSE_SCHEMA_NAME"))
        forEach { mediaType.addExamples(it.code, Example().value(ErrorResponse(it.code, it.message))) }
        return ApiResponse()
            .description(first().status.reasonPhrase)
            .content(Content().addMediaType("application/json", mediaType))
    }

    private fun errorResponseSchema(): Schema<*> =
        ModelConverters.getInstance()
            .resolveAsResolvedSchema(AnnotatedType(ErrorResponse::class.java))
            .schema

    companion object {
        private const val SECURITY_SCHEME_NAME = "Bearer Auth"
        private const val ERROR_RESPONSE_SCHEMA_NAME = "ErrorResponse"
    }
}
