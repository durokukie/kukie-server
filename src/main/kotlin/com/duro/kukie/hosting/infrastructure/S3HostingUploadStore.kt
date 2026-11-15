package com.duro.kukie.hosting.infrastructure

import com.duro.kukie.global.config.properties.AwsProperties
import com.duro.kukie.global.config.properties.HostingProperties
import com.duro.kukie.global.util.logger
import com.duro.kukie.hosting.application.port.out.HostingUploadStore
import com.duro.kukie.hosting.exception.HostingUnavailableException
import org.springframework.stereotype.Component
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.time.Duration

@Component
class S3HostingUploadStore(
    private val hostingProperties: HostingProperties,
    awsProperties: AwsProperties,
) : HostingUploadStore {

    private val log = logger()

    private val s3Client = S3Client.builder()
        .region(Region.of(awsProperties.region))
        .overrideConfiguration { it.apiCallTimeout(API_CALL_TIMEOUT) }
        .build()

    private val presigner = S3Presigner.builder().region(Region.of(awsProperties.region)).build()

    override fun presignPut(key: String, bytes: Long, ttl: Duration): String = call {
        presigner.presignPutObject { presign ->
            presign.signatureDuration(ttl).putObjectRequest {
                it.bucket(hostingProperties.stateBucket).key(key).contentLength(bytes).contentType(CONTENT_TYPE)
            }
        }.url().toString()
    }

    override fun size(key: String): Long? = call {
        try {
            s3Client.headObject { it.bucket(hostingProperties.stateBucket).key(key) }.contentLength()
        } catch (_: NoSuchKeyException) {
            null
        } catch (exception: S3Exception) {
            // HeadObject 는 본문이 없어 없는 키도 404 로만 온다
            if (exception.statusCode() == 404) null else throw exception
        }
    }

    private fun <T> call(block: () -> T): T =
        try {
            block()
        } catch (exception: SdkException) {
            log.warn("호스팅 업로드 버킷을 쓰지 못했습니다", exception)
            throw HostingUnavailableException()
        }

    companion object {
        const val CONTENT_TYPE = "application/gzip"
        private val API_CALL_TIMEOUT: Duration = Duration.ofSeconds(10)
    }
}
