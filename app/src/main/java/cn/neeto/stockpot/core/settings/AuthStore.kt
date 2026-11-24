package cn.neeto.stockpot.core.settings

import android.content.Context

data class Account(
    val id: String = "",
    val email: String = "",
    val name: String = "",
    val role: String = "user",
    val hasAvatar: Boolean = false,
    /** 头像版本。换一次头像它就变一次，界面靠它决定要不要重新拉图。 */
    val avatarAt: String = "",
    val twoFactor: Boolean = false,
) {
    val label: String get() = name.ifBlank { email.substringBefore('@') }

    /** 没设头像时用的那个圆圈里显示的字：中文取末字，英文取首字母。 */
    val initial: String
        get() {
            val base = label.trim()
            if (base.isEmpty()) return "?"
            val first = base.first()
            return if (first.code > 0x2E80) base.takeLast(1) else first.uppercase()
        }
}

/**
 * 登录状态。和账本分开放：退出登录只清这一个文件，
 * 本地账本一个字都不动——没登录的时候应用该怎么用还怎么用。
 */
class AuthStore(context: Context) {

    private val prefs = context.getSharedPreferences("stockpot_auth", Context.MODE_PRIVATE)

    var token: String
        get() = prefs.getString("token", "").orEmpty()
        private set(value) = prefs.edit().putString("token", value).apply()

    var account: Account
        get() = Account(
            id = prefs.getString("id", "").orEmpty(),
            email = prefs.getString("email", "").orEmpty(),
            name = prefs.getString("name", "").orEmpty(),
            role = prefs.getString("role", "user").orEmpty(),
            hasAvatar = prefs.getBoolean("has_avatar", false),
            avatarAt = prefs.getString("avatar_at", "").orEmpty(),
            twoFactor = prefs.getBoolean("two_factor", false),
        )
        private set(value) = prefs.edit()
            .putString("id", value.id)
            .putString("email", value.email)
            .putString("name", value.name)
            .putString("role", value.role)
            .putBoolean("has_avatar", value.hasAvatar)
            .putString("avatar_at", value.avatarAt)
            .putBoolean("two_factor", value.twoFactor)
            .apply()

    var lastSync: String
        get() = prefs.getString("last_sync", "").orEmpty()
        set(value) = prefs.edit().putString("last_sync", value).apply()

    /** 本机自上次同步之后有没有改过。决定冲突时以哪边为准。 */
    var dirty: Boolean
        get() = prefs.getBoolean("dirty", false)
        set(value) = prefs.edit().putBoolean("dirty", value).apply()

    fun save(token: String, account: Account) {
        this.token = token
        this.account = account
        this.lastSync = ""
        this.dirty = true
    }

    /** 服务器上的昵称改了、角色变了，同步回来但不动 token 和同步进度。 */
    fun updateAccount(account: Account) {
        this.account = account
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
