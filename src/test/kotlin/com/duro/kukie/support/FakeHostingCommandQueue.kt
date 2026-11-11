package com.duro.kukie.support

import com.duro.kukie.hosting.application.port.out.HostingCommandQueue
import com.duro.kukie.hosting.domain.HostingCommand

class FakeHostingCommandQueue : HostingCommandQueue, Resettable {

    val commands = mutableListOf<HostingCommand>()

    override fun send(command: HostingCommand) {
        commands += command
    }

    override fun clear() {
        commands.clear()
    }
}
