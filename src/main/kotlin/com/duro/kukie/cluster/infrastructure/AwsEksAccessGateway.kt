package com.duro.kukie.cluster.infrastructure

import com.duro.kukie.cluster.application.port.out.AwsTarget
import com.duro.kukie.cluster.application.port.out.EksAccessGateway
import com.duro.kukie.cluster.application.port.out.EksClusterEndpoint
import com.duro.kukie.cluster.application.port.out.EksClusterSummary
import com.duro.kukie.cluster.exception.AwsRoleNotAssumableException
import com.duro.kukie.cluster.exception.EksAccessDeniedException
import com.duro.kukie.cluster.exception.EksAccessEntryDisabledException
import com.duro.kukie.cluster.exception.EksClusterNotFoundException
import com.duro.kukie.cluster.exception.EksClusterNotReadyException
import com.duro.kukie.global.config.properties.ClusterAwsProperties
import org.springframework.stereotype.Component
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.eks.EksClient
import software.amazon.awssdk.services.eks.model.AccessDeniedException
import software.amazon.awssdk.services.eks.model.AccessScope
import software.amazon.awssdk.services.eks.model.AccessScopeType
import software.amazon.awssdk.services.eks.model.AuthenticationMode
import software.amazon.awssdk.services.eks.model.ResourceInUseException
import software.amazon.awssdk.services.eks.model.ResourceNotFoundException
import software.amazon.awssdk.services.sts.StsClient
import software.amazon.awssdk.services.sts.model.StsException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Component
class AwsEksAccessGateway(
    private val stsClient: StsClient,
    private val clusterAwsProperties: ClusterAwsProperties,
) : EksAccessGateway {

    private val clock: Clock = Clock.systemUTC()
    private val tokenSigner = EksTokenSigner(clock)
    private val sessions = ConcurrentHashMap<AwsTarget, AwsSessionCredentials>()

    override fun listClusters(target: AwsTarget, regions: List<String>): List<EksClusterSummary> {
        val credentials = assume(target)
        return regions.flatMap { region ->
            eks(credentials, region).use { eks ->
                translate {
                    eks.listClustersPaginator().clusters().map { name ->
                        val cluster = eks.describeCluster { it.name(name) }.cluster()
                        EksClusterSummary(name = name, region = region, status = cluster.statusAsString(), version = cluster.version())
                    }
                }
            }
        }
    }

    override fun describeCluster(target: AwsTarget, region: String, clusterName: String): EksClusterEndpoint {
        val cluster = eks(assume(target), region).use { eks ->
            translate { eks.describeCluster { it.name(clusterName) }.cluster() }
        }
        if (cluster.accessConfig()?.authenticationMode() == AuthenticationMode.CONFIG_MAP) {
            throw EksAccessEntryDisabledException()
        }
        val endpoint = cluster.endpoint()
        val caData = cluster.certificateAuthority()?.data()
        if (endpoint.isNullOrBlank() || caData.isNullOrBlank()) throw EksClusterNotReadyException()

        return EksClusterEndpoint(endpoint = endpoint.trimEnd('/'), caData = caData)
    }

    override fun registerSelf(target: AwsTarget, region: String, clusterName: String, namespace: String) {
        val principalArn = roleArn(target)
        eks(assume(target), region).use { eks ->
            translate<Unit> {
                try {
                    eks.createAccessEntry { it.clusterName(clusterName).principalArn(principalArn).type(STANDARD_ENTRY) }
                } catch (_: ResourceInUseException) {
                    // 이미 만든 access entry 다
                }

                val current = eks.listAssociatedAccessPolicies { it.clusterName(clusterName).principalArn(principalArn) }
                    .associatedAccessPolicies()
                    .firstOrNull { it.policyArn() == EDIT_POLICY_ARN }
                    ?.accessScope()
                if (current?.type() == AccessScopeType.CLUSTER || namespace in current?.namespaces().orEmpty()) return@translate

                // 정책을 다시 붙이면 범위가 바뀌므로 같은 EKS 에 먼저 등록한 namespace 를 함께 넣는다
                val namespaces = (current?.namespaces().orEmpty() + namespace).distinct().sorted()
                eks.associateAccessPolicy {
                    it.clusterName(clusterName)
                        .principalArn(principalArn)
                        .policyArn(EDIT_POLICY_ARN)
                        .accessScope(AccessScope.builder().type(AccessScopeType.NAMESPACE).namespaces(namespaces).build())
                }
            }
        }
    }

    override fun issueToken(target: AwsTarget, region: String, clusterName: String): String =
        tokenSigner.sign(assume(target), region, clusterName)

    private fun assume(target: AwsTarget): AwsSessionCredentials {
        val now = Instant.now(clock)
        sessions[target]?.takeIf { it.expirationTime().map { expiry -> expiry.isAfter(now + REUSE_MARGIN) }.orElse(false) }
            ?.let { return it }

        val credentials = try {
            stsClient.assumeRole {
                it.roleArn(roleArn(target))
                    .externalId(target.externalId)
                    .roleSessionName(target.externalId)
                    .durationSeconds(clusterAwsProperties.sessionSeconds)
            }.credentials()
        } catch (exception: StsException) {
            if (exception.statusCode() == FORBIDDEN) throw AwsRoleNotAssumableException()
            throw exception
        }

        return AwsSessionCredentials.builder()
            .accessKeyId(credentials.accessKeyId())
            .secretAccessKey(credentials.secretAccessKey())
            .sessionToken(credentials.sessionToken())
            .expirationTime(credentials.expiration())
            .build()
            .also { sessions[target] = it }
    }

    private fun eks(credentials: AwsSessionCredentials, region: String): EksClient =
        EksClient.builder()
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(credentials))
            .overrideConfiguration { it.apiCallTimeout(API_CALL_TIMEOUT) }
            .build()

    private fun roleArn(target: AwsTarget): String =
        "arn:aws:iam::${target.accountId}:role/${clusterAwsProperties.roleNamePrefix}${target.externalId}"

    private fun <T> translate(block: () -> T): T =
        try {
            block()
        } catch (_: ResourceNotFoundException) {
            throw EksClusterNotFoundException()
        } catch (_: AccessDeniedException) {
            throw EksAccessDeniedException()
        }

    companion object {
        private const val STANDARD_ENTRY = "STANDARD"
        private const val EDIT_POLICY_ARN = "arn:aws:eks::aws:cluster-access-policy/AmazonEKSEditPolicy"
        private const val FORBIDDEN = 403
        private val API_CALL_TIMEOUT: Duration = Duration.ofSeconds(10)

        /** 재사용한 세션으로 만든 토큰도 kubectl 이 끝날 때까지는 살아 있어야 한다. */
        private val REUSE_MARGIN: Duration = Duration.ofMinutes(5)
    }
}
