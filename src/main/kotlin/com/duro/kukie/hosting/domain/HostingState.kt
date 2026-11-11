package com.duro.kukie.hosting.domain

import java.time.Instant

/** 배포기가 적는 파일의 모양. 모르는 필드는 무시하고, 모르는 version 의 파일은 읽지 않는다. */
interface HostingFile {
    val version: Int

    companion object {
        const val VERSION = 1
    }
}

data class HostingAccount(val id: Long, val login: String? = null, val type: String? = null)

data class HostingRepo(val id: Long? = null, val fullName: String)

data class HostingCommit(val sha: String, val message: String? = null)

data class HostingConfig(val port: Int, val size: String, val source: String)

data class HostingFailure(val code: String, val message: String)

data class HostingLatest(
    val deploymentId: String,
    val commandId: String? = null,
    val phase: String,
    val commit: HostingCommit,
    val at: Instant,
)

data class HostingApp(
    override val version: Int,
    val owner: HostingAccount,
    val repo: HostingRepo,
    val team: String,
    val app: String,
    val generation: String,
    val url: String,
    val config: HostingConfig,
    val latest: HostingLatest,
) : HostingFile

data class HostingDeployment(
    override val version: Int,
    val deploymentId: String,
    val phase: String,
    val trigger: String,
    val commandId: String? = null,
    val commit: HostingCommit,
    val config: HostingConfig,
    val imageReused: Boolean,
    val hasLog: Boolean,
    val failure: HostingFailure? = null,
    val startedAt: Instant,
    val updatedAt: Instant,
) : HostingFile

data class SkippedPush(
    override val version: Int,
    val sender: HostingAccount,
    val repo: HostingRepo,
    val reason: String,
    val at: Instant,
) : HostingFile
