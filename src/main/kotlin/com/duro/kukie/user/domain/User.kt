package com.duro.kukie.user.domain

import com.duro.kukie.global.domain.Email
import com.duro.kukie.global.entity.BaseTimeEntity
import com.github.f4b6a3.uuid.UuidCreator
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.UUID

@Entity
@Table(name = "tbl_user")
class User private constructor(
    name: String,
    email: Email,
    password: String?,
) : BaseTimeEntity() {

    constructor(name: String, email: Email, rawPassword: String, passwordEncoder: PasswordEncoder) : this(
        name = name,
        email = email,
        password = checkNotNull(passwordEncoder.encode(rawPassword))
    )

    constructor(name: String, email: Email) : this(
        name = name,
        email = email,
        password = null,
    )

    @Id
    var id: UUID = UuidCreator.getTimeOrderedEpoch()
        protected set

    @Column(nullable = false, length = 50)
    var name = name
        protected set

    @Column(nullable = false, unique = true, length = 255)
    var email = email
        protected set

    @Column(nullable = true, length = 255)
    var password = password
        protected set

    @Column(name = "avatar_url", length = AVATAR_URL_MAX_LENGTH)
    var avatarUrl: String? = null
        protected set

    // 호스팅 앱의 주인은 이 숫자 id 로 가린다. 계정명은 바뀌고 다른 사람이 다시 쓸 수 있다
    @Column(name = "github_id", unique = true)
    var githubId: Long? = null
        protected set

    @Column(name = "github_login", length = GITHUB_LOGIN_MAX_LENGTH)
    var githubLogin: String? = null
        protected set

    val hasPassword: Boolean
        get() = password != null

    fun matchesPassword(rawPassword: String, passwordEncoder: PasswordEncoder): Boolean =
        password?.let { passwordEncoder.matches(rawPassword, it) } ?: false

    fun changePassword(rawPassword: String, passwordEncoder: PasswordEncoder) {
        password = checkNotNull(passwordEncoder.encode(rawPassword))
    }

    fun update(name: String) {
        this.name = name
    }

    /** https 만 받는다(화면이 img 로 띄운다). 사진을 안 주는 로그인은 있던 사진을 지우지 않는다. */
    fun changeAvatar(url: String?) {
        if (url != null && url.startsWith("https://") && url.length <= AVATAR_URL_MAX_LENGTH) avatarUrl = url
    }

    fun linkGitHub(account: GitHubAccount) {
        githubId = account.id
        githubLogin = account.login.take(GITHUB_LOGIN_MAX_LENGTH)
    }

    fun unlinkGitHub() {
        githubId = null
        githubLogin = null
    }

    companion object {
        const val EMAIL_UNIQUE_CONSTRAINT = "tbl_user_email_key"
        const val GITHUB_ID_UNIQUE_CONSTRAINT = "tbl_user_github_id_key"
        private const val AVATAR_URL_MAX_LENGTH = 500
        private const val GITHUB_LOGIN_MAX_LENGTH = 39
    }
}
