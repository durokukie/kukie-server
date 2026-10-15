package com.duro.kukie.chat.domain

import com.duro.kukie.global.entity.BaseTimeEntity
import com.github.f4b6a3.uuid.UuidCreator
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "tbl_chat_session")
class ChatSession(
    userId: UUID,
    clusterId: UUID,
    title: String,
    contextName: String,
    namespace: String,
    clusterFingerprint: String,
    visibility: ChatVisibility,
) : BaseTimeEntity() {

    @Id
    var id: UUID = UuidCreator.getTimeOrderedEpoch()
        protected set

    @Column(name = "user_id", nullable = false)
    var userId = userId
        protected set

    @Column(name = "cluster_id", nullable = false)
    var clusterId = clusterId
        protected set

    @Column(nullable = false, length = 200)
    var title = title
        protected set

    @Column(name = "current_mode", nullable = false, length = 20)
    var currentMode = DEFAULT_MODE
        protected set

    @Column(name = "context_name", nullable = false, length = 200)
    var contextName = contextName
        protected set

    @Column(nullable = false, length = 63)
    var namespace = namespace
        protected set

    @Column(name = "cluster_fingerprint", nullable = false, length = 64)
    var clusterFingerprint = clusterFingerprint
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var visibility = visibility
        protected set

    @Column(nullable = false)
    var version = 1
        protected set

    val hasDefaultTitle: Boolean
        get() = title == DEFAULT_TITLE

    fun touch() {
        version += 1
    }

    fun changeMode(mode: String) {
        if (currentMode == mode) return
        currentMode = mode
        touch()
    }

    fun rename(title: String) {
        this.title = title
        touch()
    }

    companion object {
        const val DEFAULT_TITLE = "새 대화"

        const val DEFAULT_MODE = "학습"
    }
}
