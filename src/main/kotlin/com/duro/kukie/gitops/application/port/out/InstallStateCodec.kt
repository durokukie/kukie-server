package com.duro.kukie.gitops.application.port.out

import com.duro.kukie.gitops.domain.InstallState

/** GitHub 설치 화면에 실어 보내는 state 를 서명하고 확인한다. */
interface InstallStateCodec {

    fun encode(state: InstallState): String

    /** 서명이 틀리거나 만료됐으면 null. */
    fun decode(token: String): InstallState?
}
