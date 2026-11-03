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
    /** 성공한 갱신 뒤 다음 갱신까지 기다리는 시간. */
    val refreshCooldown: Duration = Duration.ofMinutes(1),
    /** 갱신이 도는 동안 잠금을 쥐는 시간. 호출 시간 상한보다 길게 둬서 갱신이 겹치지 않게 하고, 서버가 죽으면 이 시간 뒤 풀린다. */
    val refreshLockTimeout: Duration = Duration.ofMinutes(5),
)
