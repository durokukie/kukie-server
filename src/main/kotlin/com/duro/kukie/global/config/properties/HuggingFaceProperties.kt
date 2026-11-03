package com.duro.kukie.global.config.properties

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.time.Duration

@Validated
@ConfigurationProperties(prefix = "hugging-face")
data class HuggingFaceProperties(
    @field:NotBlank val apiUrl: String = "https://huggingface.co/api",
    /** 없으면 익명으로 부른다. 환경 변수로만 준다. */
    val token: String? = null,
    val refreshCooldown: Duration = Duration.ofMinutes(1),
)
