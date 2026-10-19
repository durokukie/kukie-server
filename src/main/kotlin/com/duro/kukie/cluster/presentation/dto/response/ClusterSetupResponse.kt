package com.duro.kukie.cluster.presentation.dto.response

import com.duro.kukie.cluster.application.port.out.EksAddon
import com.duro.kukie.cluster.application.port.out.EksClusterSetup
import com.duro.kukie.cluster.application.port.out.EksNodegroup
import com.duro.kukie.cluster.application.port.out.EksSubnet

data class ClusterSetupResponse(
    val status: ClusterSetupStatus,
    val kubernetesVersion: String?,
    val platformVersion: String?,
    val endpointPublic: Boolean?,
    val endpointPrivate: Boolean?,
    val serviceCidr: String?,
    val addons: List<AddonResponse>,
    val nodegroups: List<NodegroupResponse>,
    val subnets: List<SubnetResponse>,
) {
    data class AddonResponse(
        val name: String,
        val version: String?,
        val status: String?,
    ) {
        companion object {
            fun from(addon: EksAddon) = AddonResponse(name = addon.name, version = addon.version, status = addon.status)
        }
    }

    data class NodegroupResponse(
        val name: String,
        val instanceTypes: List<String>,
        val capacityType: String?,
        val desired: Int?,
        val min: Int?,
        val max: Int?,
        val zones: List<String>,
    ) {
        companion object {
            fun from(nodegroup: EksNodegroup) = NodegroupResponse(
                name = nodegroup.name,
                instanceTypes = nodegroup.instanceTypes,
                capacityType = nodegroup.capacityType,
                desired = nodegroup.desired,
                min = nodegroup.min,
                max = nodegroup.max,
                zones = nodegroup.zones,
            )
        }
    }

    data class SubnetResponse(
        val zone: String,
        val cidr: String,
    ) {
        companion object {
            fun from(subnet: EksSubnet) = SubnetResponse(zone = subnet.zone, cidr = subnet.cidr)
        }
    }

    companion object {
        fun from(setup: EksClusterSetup) = ClusterSetupResponse(
            status = if (setup.inventory == null) ClusterSetupStatus.PERMISSION_REQUIRED else ClusterSetupStatus.OK,
            kubernetesVersion = setup.kubernetesVersion,
            platformVersion = setup.platformVersion,
            endpointPublic = setup.endpointPublic,
            endpointPrivate = setup.endpointPrivate,
            serviceCidr = setup.serviceCidr,
            addons = setup.inventory?.addons.orEmpty().map(AddonResponse::from),
            nodegroups = setup.inventory?.nodegroups.orEmpty().map(NodegroupResponse::from),
            subnets = setup.inventory?.subnets.orEmpty().map(SubnetResponse::from),
        )

        fun unsupported() = ClusterSetupResponse(
            status = ClusterSetupStatus.UNSUPPORTED,
            kubernetesVersion = null,
            platformVersion = null,
            endpointPublic = null,
            endpointPrivate = null,
            serviceCidr = null,
            addons = emptyList(),
            nodegroups = emptyList(),
            subnets = emptyList(),
        )
    }
}
