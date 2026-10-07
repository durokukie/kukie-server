package com.duro.kukie.global.config

import com.duro.kukie.global.config.properties.AwsProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sesv2.SesV2Client
import java.time.Duration

@Configuration
class AwsSesConfig {

    @Bean
    fun sesV2Client(awsProperties: AwsProperties): SesV2Client =
        SesV2Client.builder()
            .region(Region.of(awsProperties.region))
            .overrideConfiguration { it.apiCallTimeout(API_CALL_TIMEOUT) }
            .build()

    companion object {
        private val API_CALL_TIMEOUT: Duration = Duration.ofSeconds(5)
    }
}
