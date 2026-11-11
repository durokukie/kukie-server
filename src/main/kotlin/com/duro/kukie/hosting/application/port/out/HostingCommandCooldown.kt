package com.duro.kukie.hosting.application.port.out

import com.duro.kukie.hosting.domain.HostingCommand

interface HostingCommandCooldown {

    /** 같은 앱에 같은 명령을 방금 보냈으면 false. */
    fun tryStart(command: HostingCommand): Boolean
}
