package cn.neeto.stockpot.core.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Stockpot 同步 API 的瘦客户端（对应 server/index.mjs）。
 *
 * 只有账号和「整份账本」两类请求：账本是本地优先的，
 * 断网、没登录、服务器挂了都不影响记账，联网只是把这份 JSON 备份出去。
 */

@Serializable
data class AccountDto(
    val id: String = "",
    val email: String = "",
    val name: String = "",
    val role: String = "user",
    /** 头像单独拉：每次 /me 都驮着一串 base64 太浪费 */
    val hasAvatar: Boolean = false,
    val avatarAt: String = "",
    val twoFactor: Boolean = false,
)

@Serializable
data class AuthResponse(
    val token: String = "",
    val user: AccountDto = AccountDto(),
    /** 开了两步验证：这里没有 token，只有一张五分钟的票据，换到验证码才发令牌 */
    val twoFactor: Boolean = false,
    val ticket: String = "",
)

@Serializable
data class UserEnvelope(val user: AccountDto = AccountDto())

/**
 * 二维码：服务端把 `otpauth://` 编码成模块矩阵，客户端一个方块一个方块画。
 * 这样两边都不用带图片解码库，深色主题下也不会镶一圈白边。
 */
@Serializable
data class QrMatrix(
    val size: Int = 0,
    val rows: List<String> = emptyList(),
)

@Serializable
data class TwoFactorSetup(
    val secret: String = "",
    val otpauth: String = "",
    val qr: QrMatrix = QrMatrix(),
)

/** 启用成功才给这一次恢复码，之后库里只有散列。 */
@Serializable
data class TwoFactorEnabled(
    val user: AccountDto = AccountDto(),
    val recoveryCodes: List<String> = emptyList(),
)

@Serializable
data class MeResponse(
    val user: AccountDto = AccountDto(),
    val rev: Int = 0,
    val updatedAt: String? = null,
)

/**
 * 云端那份账本。`rev` 单调递增，用来判断自己手上的是不是最新的；
 * `deleted` 是形如 `"entries:e_xxx" -> "2026-11-20T..."` 的墓碑，
 * 记录"这条被删了、什么时候删的"，免得多设备之间删掉的东西又冒回来。
 */
@Serializable
data class LedgerDoc(
    val rev: Int = 0,
    /** 还没传过账本时服务端给的是 null */
    val updatedAt: String? = null,
    val ledger: JsonObject? = null,
    val deleted: Map<String, String> = emptyMap(),
)

@Serializable
data class LedgerPush(
    val baseRev: Int,
    val ledger: JsonObject,
    val deleted: Map<String, String> = emptyMap(),
)

/** 推上去成功，或者被告知"你手上的是旧的，先合一下"。 */
sealed interface PushResult {
    data class Ok(val doc: LedgerDoc) : PushResult
    data class Conflict(val doc: LedgerDoc) : PushResult
}

/**
 * 服务器上最新的那个版本。`versionCode` 才是判断要不要更新的依据，
 * `sha256` 拿回来校验下载下来的包，`downloadUrl` 是完整地址（自建服务器也说得清）。
 */
@Serializable
data class AppRelease(
    val id: String = "",
    val versionCode: Int = 0,
    val versionName: String = "",
    val notes: String = "",
    val force: Boolean = false,
    val size: Long = 0,
    val sha256: String = "",
    val publishedAt: String? = null,
    val downloadUrl: String = "",
)

@Serializable
data class LatestRelease(
    val latest: AppRelease? = null,
    val hasUpdate: Boolean = false,
)

class ApiException(val status: Int, override val message: String) : IOException(message)

class ApiClient(
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        // 几万条流水的账本可能有几 MB，写超时给宽一点
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun login(base: String, email: String, password: String): AuthResponse = call(
        base,
        "/api/v1/auth/login",
        body = """{"email":${quote(email)},"password":${quote(password)}}""",
        token = null,
        method = "POST",
    )

    /** 登录第二步：六位验证码或一次性恢复码，换到正式令牌。 */
    suspend fun loginTwoFactor(base: String, ticket: String, code: String): AuthResponse = call(
        base,
        "/api/v1/auth/2fa",
        body = """{"ticket":${quote(ticket)},"code":${quote(code)}}""",
        token = null,
        method = "POST",
    )

    suspend fun register(base: String, email: String, password: String, name: String): AuthResponse = call(
        base,
        "/api/v1/auth/register",
        body = """{"email":${quote(email)},"password":${quote(password)},"name":${quote(name)}}""",
        token = null,
        method = "POST",
    )

    suspend fun me(base: String, token: String): MeResponse = call(base, "/api/v1/me", null, token)

    /* ------------------------------ 账号资料 ------------------------------ */

    suspend fun updateName(base: String, token: String, name: String): UserEnvelope = call(
        base,
        "/api/v1/me",
        body = """{"name":${quote(name)}}""",
        token = token,
        method = "PATCH",
    )

    suspend fun changePassword(base: String, token: String, current: String, next: String): UserEnvelope = call(
        base,
        "/api/v1/me/password",
        body = """{"currentPassword":${quote(current)},"newPassword":${quote(next)}}""",
        token = token,
        method = "POST",
    )

    /** `image` 是完整的 data URL（`data:image/jpeg;base64,...`），服务端只认 png/jpeg/webp。 */
    suspend fun putAvatar(base: String, token: String, dataUrl: String): UserEnvelope = call(
        base,
        "/api/v1/me/avatar",
        body = """{"image":${quote(dataUrl)}}""",
        token = token,
        method = "PUT",
    )

    suspend fun deleteAvatar(base: String, token: String): UserEnvelope = call(
        base,
        "/api/v1/me/avatar",
        body = null,
        token = token,
        method = "DELETE",
    )

    /** 头像是裸的图片字节，不走 json 解码。 */
    suspend fun avatarBytes(base: String, token: String): ByteArray = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(path(base, "/api/v1/me/avatar"))
            .get()
            .header("authorization", "Bearer $token")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body
            if (!response.isSuccessful || body == null) {
                throw ApiException(response.code, "头像没拉到")
            }
            body.bytes()
        }
    }

    suspend fun setupTwoFactor(base: String, token: String): TwoFactorSetup = call(
        base,
        "/api/v1/me/2fa/setup",
        body = "{}",
        token = token,
        method = "POST",
    )

    suspend fun enableTwoFactor(base: String, token: String, code: String): TwoFactorEnabled = call(
        base,
        "/api/v1/me/2fa/enable",
        body = """{"code":${quote(code)}}""",
        token = token,
        method = "POST",
    )

    suspend fun disableTwoFactor(base: String, token: String, password: String): UserEnvelope = call(
        base,
        "/api/v1/me/2fa/disable",
        body = """{"password":${quote(password)}}""",
        token = token,
        method = "POST",
    )

    suspend fun pull(base: String, token: String): LedgerDoc =
        call(base, "/api/v1/ledger", null, token)

    /**
     * 整份快照推上去。服务端用 `baseRev` 做乐观锁：对不上就回 409，
     * 顺手把云端那份一起带回来，让调用方合并完再推一次。
     */
    suspend fun push(base: String, token: String, payload: LedgerPush): PushResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(path(base, "/api/v1/ledger"))
            .put(
                json.encodeToString(LedgerPush.serializer(), payload)
                    .toRequestBody(JSON_MEDIA),
            )
            .header("accept", "application/json")
            .header("authorization", "Bearer $token")
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            when {
                response.isSuccessful -> PushResult.Ok(decode(raw, response.code))
                response.code == 409 -> PushResult.Conflict(decode(raw, response.code))
                else -> throw failure(response.code, raw)
            }
        }
    }

    suspend fun clearRemote(base: String, token: String): LedgerDoc = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(path(base, "/api/v1/ledger"))
            .delete()
            .header("accept", "application/json")
            .header("authorization", "Bearer $token")
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw failure(response.code, raw)
            decode<LedgerDoc>(raw, response.code)
        }
    }

    /* ------------------------------ 版本 ------------------------------ */

    /** 查最新版本。不需要登录：没账号的人也该能升级。 */
    suspend fun latestRelease(base: String, versionCode: Int): LatestRelease =
        call(base, "/api/v1/app/latest?versionCode=$versionCode", null, null)

    /**
     * 把安装包下到 [target]，顺手算 sha256 给调用方校验。
     *
     * 用 OkHttp 的流读，不整个读进内存——几十兆的包在手机上不该多占一份。
     * [onProgress] 拿不到总长度时不会回调，界面就显示不确定进度。
     */
    suspend fun downloadApk(url: String, target: File, onProgress: (Float) -> Unit): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw failure(response.code, response.body?.string().orEmpty())
                val body = response.body ?: throw ApiException(response.code, "服务器没有返回安装包")
                val total = body.contentLength()
                val digest = MessageDigest.getInstance("SHA-256")
                var read = 0L
                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            read += count
                            if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
                if (read <= 0L) throw ApiException(response.code, "下载到的安装包是空的")
                digest.digest().joinToString("") { "%02x".format(it) }
            }
        }

    private suspend inline fun <reified T> call(
        base: String,
        route: String,
        body: String?,
        token: String?,
        method: String = "GET",
    ): T = withContext(Dispatchers.IO) {
        val payload = body?.toRequestBody(JSON_MEDIA)
        val request = Request.Builder()
            .url(path(base, route))
            .method(method, if (method == "GET") null else payload)
            .header("accept", "application/json")
            .apply { if (!token.isNullOrBlank()) header("authorization", "Bearer $token") }
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw failure(response.code, raw)
            decode(raw, response.code)
        }
    }

    private inline fun <reified T> decode(raw: String, code: Int): T {
        if (raw.isBlank()) throw ApiException(code, "服务器返回了空内容")
        return runCatching { json.decodeFromString<T>(raw) }.getOrElse {
            throw ApiException(code, "看不懂服务器返回的内容")
        }
    }

    /** 服务端统一用 `{error, message}` 报错，message 是给人看的。 */
    private fun failure(code: Int, raw: String): ApiException {
        val text = runCatching {
            val obj = json.parseToJsonElement(raw) as? JsonObject
            (obj?.get("message") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: (obj?.get("error") as? JsonPrimitive)?.contentOrNull
        }.getOrNull()
        return ApiException(code, text?.takeIf { it.isNotBlank() } ?: "HTTP $code")
    }

    private fun path(base: String, route: String): String = base.trim().trimEnd('/') + route

    private fun quote(value: String): String = JsonPrimitive(value).toString()

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
