package com.duro.kukie.global.config

import com.duro.kukie.global.config.properties.AwsProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sts.StsClient
import java.time.Duration

@Configuration
class AwsStsConfig {

    @Bean
    fun stsClient(awsProperties: AwsProperties): StsClient =
        StsClient.builder()
            .region(Region.of(awsProperties.region))
            .overrideConfiguration { it.apiCallTimeout(API_CALL_TIMEOUT) }
            .build()

    companion object {
        private val API_CALL_TIMEOUT: Duration = Duration.ofSeconds(10)
    }
}
