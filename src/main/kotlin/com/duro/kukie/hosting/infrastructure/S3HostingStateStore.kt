package com.duro.kukie.hosting.infrastructure

import com.duro.kukie.global.config.properties.AwsProperties
import com.duro.kukie.global.config.properties.HostingProperties
import com.duro.kukie.global.util.logger
import com.duro.kukie.hosting.application.port.out.HostingStateStore
import com.duro.kukie.hosting.exception.HostingUnavailableException
import org.springframework.stereotype.Component
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.time.Duration

@Component
class S3HostingStateStore(
    private val hostingProperties: HostingProperties,
    awsProperties: AwsProperties,
) : HostingStateStore {

    private val log = logger()

    private val s3Client = S3Client.builder()
        .region(Region.of(awsProperties.region))
        .overrideConfiguration { it.apiCallTimeout(API_CALL_TIMEOUT) }
        .build()

    private val presigner = S3Presigner.builder().region(Region.of(awsProperties.region)).build()

    override fun list(prefix: String): List<String> = call {
        s3Client.listObjectsV2Paginator { it.bucket(hostingProperties.stateBucket).prefix(prefix) }.contents().map { it.key() }
    }

    override fun listFolders(prefix: String): List<String> = call {
        s3Client.listObjectsV2Paginator { it.bucket(hostingProperties.stateBucket).prefix(prefix).delimiter("/") }
            .commonPrefixes().map { it.prefix() }
    }

    // 페이지를 필요한 만큼만 받는다: 앱 로그는 한 달에 수만 개가 될 수 있다
    override fun listBetween(prefix: String, startAfter: String, until: String): List<String> = call {
        s3Client.listObjectsV2Paginator { it.bucket(hostingProperties.stateBucket).prefix(prefix).startAfter(startAfter) }
            .contents().asSequence().map { it.key() }.takeWhile { it <= until }.toList()
    }

    override fun presignGet(key: String, ttl: Duration, filename: String): String = call {
        presigner.presignGetObject { presign ->
            presign.signatureDuration(ttl).getObjectRequest {
                it.bucket(hostingProperties.stateBucket).key(key).responseContentDisposition("attachment; filename=\"$filename\"")
            }
        }.url().toString()
    }

    // ponytail: 빌드 로그(최대 10MB)를 통째로 메모리에 올린다. 동시 조회가 많아지면 스트림으로 바꾼다
    override fun read(key: String): String? = call {
        try {
            s3Client.getObjectAsBytes { it.bucket(hostingProperties.stateBucket).key(key) }.asUtf8String()
        } catch (exception: NoSuchKeyException) {
            null
        }
    }

    // 앞쪽이 잘려 UTF-8 글자 중간에서 시작할 수 있어, 깨진 첫 글자는 대체 문자로 읽힌다
    override fun readTail(key: String, bytes: Int): String? = call {
        try {
            s3Client.getObjectAsBytes { it.bucket(hostingProperties.stateBucket).key(key).range("bytes=-$bytes") }.asUtf8String()
        } catch (exception: NoSuchKeyException) {
            null
        }
    }

    private fun <T> call(block: () -> T): T =
        try {
            block()
        } catch (exception: SdkException) {
            log.warn("호스팅 상태 버킷을 읽지 못했습니다", exception)
            throw HostingUnavailableException()
        }

    companion object {
        private val API_CALL_TIMEOUT: Duration = Duration.ofSeconds(10)
    }
}
