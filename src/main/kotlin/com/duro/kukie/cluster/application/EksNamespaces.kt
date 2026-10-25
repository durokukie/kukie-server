package com.duro.kukie.cluster.application

import com.duro.kukie.cluster.application.port.out.CredentialCipher
import com.duro.kukie.cluster.domain.ClusterProvider
import com.duro.kukie.cluster.domain.ClusterRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class EksNamespaces(
    private val clusterRepository: ClusterRepository,
    private val credentialCipher: CredentialCipher,
) {

    /** 같은 팀이 같은 EKS 로 등록해 둔 namespace 들. access entry 범위를 줄일 때 남길 값이다. */
    fun registeredOn(teamId: UUID, credential: EksCredential, except: UUID? = null): Set<String> =
        clusterRepository.findAllByTeamIdAndProvider(teamId, ClusterProvider.EKS)
            .filter { it.id != except }
            .filter { EksCredential.from(credentialCipher.decrypt(it.credential)) == credential }
            .map { it.defaultNamespace }
            .toSet()
}
