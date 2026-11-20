package com.duro.kukie.hosting.application

import com.duro.kukie.hosting.application.port.out.HostingUploadStore
import com.duro.kukie.hosting.domain.HostingApp
import com.duro.kukie.hosting.domain.HostingCommand
import com.duro.kukie.hosting.exception.HostingAppSourceMismatchException
import com.duro.kukie.hosting.exception.HostingUploadMissingException
import com.duro.kukie.hosting.presentation.dto.response.HostingCommandResponse
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class SendHostingCommandService(
    private val hostingApps: HostingApps,
    private val hostingCommandSender: HostingCommandSender,
    private val hostingUploadStore: HostingUploadStore,
) {

    /**
     * 배포기는 누가 요청했는지 보지 않으므로, 큐에 넣기 전에 여기서 앱의 주인인지 확인한다.
     * 업로드로 만든 앱의 재배포는 마지막 배포에 쓴 업로드를 다시 보낸다. 업로드는 하루만 보관해서 지났으면 다시 올리게 한다.
     */
    operator fun invoke(userId: UUID, app: String, type: String): HostingCommandResponse {
        val owned = hostingApps.requireOwned(userId, app)
        hostingApps.requireNotSuspended(owned)
        val command = if (type == HostingCommand.REDEPLOY && owned.repo == null) reupload(owned) else HostingCommand.of(type, owned)

        return hostingCommandSender.send(userId, command)
    }

    private fun reupload(app: HostingApp): HostingCommand {
        if (!app.uploaded) throw HostingAppSourceMismatchException()
        if (!UPLOAD_ID.matches(app.latest.commit.sha)) throw HostingUploadMissingException(UPLOAD_GONE)
        val command = HostingCommand.reupload(app)
        if (hostingUploadStore.size(command.source!!.key) == null) throw HostingUploadMissingException(UPLOAD_GONE)
        return command
    }

    companion object {
        private val UPLOAD_ID = Regex("[0-9a-f]{40}")
        private const val UPLOAD_GONE = "마지막으로 올린 폴더는 하루만 보관해서 화면에서 다시 배포할 수 없어요. 코딩 에이전트로 다시 배포해 주세요."
    }
}
