package com.duro.kukie.cluster.application.port.out

interface KubeconfigParser {

    fun parse(kubeconfig: String, contextName: String?): ParsedKubeconfig
}
