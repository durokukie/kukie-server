package com.duro.kukie.cluster.application.port.out

/** 클러스터 지도에 그릴 EKS 구성. inventory 가 null 이면 고객 역할이 템플릿 v3 이전이라 애드온·노드 그룹·서브넷을 읽지 못한 것이다. */
data class EksClusterSetup(
    val kubernetesVersion: String?,
    val platformVersion: String?,
    val endpointPublic: Boolean?,
    val endpointPrivate: Boolean?,
    val serviceCidr: String?,
    val inventory: EksClusterInventory?,
)

data class EksClusterInventory(
    val addons: List<EksAddon>,
    val nodegroups: List<EksNodegroup>,
    val subnets: List<EksSubnet>,
)

data class EksAddon(
    val name: String,
    val version: String?,
    val status: String?,
)

data class EksNodegroup(
    val name: String,
    val instanceTypes: List<String>,
    val capacityType: String?,
    val desired: Int?,
    val min: Int?,
    val max: Int?,
    val zones: List<String>,
)

data class EksSubnet(
    val zone: String,
    val cidr: String,
)
