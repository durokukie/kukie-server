package com.duro.kukie.cluster.application

import com.duro.kukie.cluster.application.port.out.AwsTarget

/** EKS 클러스터가 credential 칸에 암호화해 두는 값. 토큰은 저장하지 않고 연결할 때마다 새로 만든다. */
data class EksCredential(
    val accountId: String,
    val externalId: String,
    val region: String,
    val clusterName: String,
) {
    val target: AwsTarget
        get() = AwsTarget(accountId = accountId, externalId = externalId)

    fun toMap(): Map<String, String> = mapOf(
        "type" to TYPE,
        "accountId" to accountId,
        "externalId" to externalId,
        "region" to region,
        "clusterName" to clusterName,
    )

    companion object {
        private const val TYPE = "eks"

        fun from(credential: Map<String, String>): EksCredential {
            check(credential["type"] == TYPE) { "Stored credential is not an EKS credential" }
            return EksCredential(
                accountId = credential.getValue("accountId"),
                externalId = credential.getValue("externalId"),
                region = credential.getValue("region"),
                clusterName = credential.getValue("clusterName"),
            )
        }
    }
}
