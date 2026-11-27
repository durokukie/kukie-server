package com.duro.kukie.cluster.application.port.out

import com.duro.kukie.cluster.domain.Cluster

/** 사용자 요청 없이 서버가 클러스터의 Kubernetes API 를 읽는다. 읽기(GET)만 하고, 자격증명은 서버 밖으로 나가지 않는다. */
interface ClusterApi {

    /** 붙을 수 없는 클러스터면 예외를 던진다. block 안에서도 닿지 못하면 던진다. */
    fun <T> read(cluster: Cluster, block: (Session) -> T): T

    fun interface Session {

        /** path 는 `/api/...` 처럼 슬래시로 시작하고 쿼리까지 담는다. */
        fun get(path: String): Response
    }

    data class Response(val status: Int, val body: String)
}
