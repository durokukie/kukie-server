package com.duro.kukie.cluster.application.port.out

interface ApiServerAddressChecker {

    /** 받아들일 수 없는 주소면 사용자에게 보여 줄 사유를, 괜찮으면 null 을 돌려준다. */
    fun rejectionOf(apiServer: String): String?
}
