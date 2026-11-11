package com.duro.kukie.hosting.application.port.out

import com.duro.kukie.hosting.domain.HostingCommand

interface HostingCommandQueue {
    fun send(command: HostingCommand)
}
