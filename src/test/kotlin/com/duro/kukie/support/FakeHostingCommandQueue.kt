package com.duro.kukie.support

import com.duro.kukie.hosting.application.port.out.HostingCommandQueue
import com.duro.kukie.hosting.domain.HostingCommand
import com.duro.kukie.hosting.exception.HostingUnavailableException

class FakeHostingCommandQueue : HostingCommandQueue, Resettable {

    val commands = mutableListOf<HostingCommand>()

    var shouldFail = false

    override fun send(command: HostingCommand) {
        if (shouldFail) throw HostingUnavailableException()
        commands += command
    }

    override fun clear() {
        commands.clear()
        shouldFail = false
    }
}
