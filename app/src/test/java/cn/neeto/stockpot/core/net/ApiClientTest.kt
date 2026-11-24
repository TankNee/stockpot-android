package cn.neeto.stockpot.core.net

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

/**
 * 用 MockWebServer 把 ApiClient 的协议走一遍。
 *
 * 这里最容易错的地方不是合并逻辑，而是「哪个接口用 POST、哪个用 PUT」
 * 和「服务端回 null 的字段能不能读进来」——编译期都看不出来，只能对着发。
 */
class ApiClientTest {

    private lateinit var server: MockWebServer
    private lateinit var base: String

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        base = server.url("/").toString().trimEnd('/')
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun ok(body: String, code: Int = 200) {
        server.enqueue(
            MockResponse()
                .setResponseCode(code)
                .setHeader("content-type", "application/json")
                .setBody(body),
        )
    }

    @Test
    fun `登录走 POST，并且带得回 token`() = runBlocking {
        ok("""{"token":"tok","user":{"id":"u1","email":"a@b.c","name":"tongke","role":"user"}}""")

        val response = ApiClient().login(base, "a@b.c", "12345678")

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/v1/auth/login", recorded.path)
        assertEquals("tok", response.token)
        assertEquals("tongke", response.user.name)
        assertTrue(recorded.body.readUtf8().contains("\"password\":\"12345678\""))
    }

    @Test
    fun `注册走 POST`() = runBlocking {
        ok("""{"token":"tok2","user":{"id":"u2","email":"c@d.e","name":"n","role":"user"}}""", code = 201)

        val response = ApiClient().register(base, "c@d.e", "12345678", "n")

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("tok2", response.token)
        assertTrue(recorded.body.readUtf8().contains("\"name\":\"n\""))
    }

    @Test
    fun `全新的账号读得进 null 的 updatedAt`() = runBlocking {
        ok("""{"rev":0,"updatedAt":null,"ledger":null,"deleted":{}}""")

        val doc = ApiClient().pull(base, "tok")

        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("Bearer tok", recorded.getHeader("authorization"))
        assertEquals(0, doc.rev)
        assertEquals(null, doc.updatedAt)
        assertEquals(null, doc.ledger)
    }

    @Test
    fun `推送时服务端说版本不对就变成 Conflict`() = runBlocking {
        ok("""{"rev":7,"updatedAt":"2026-11-23T10:00:00Z","ledger":{"entries":[]},"deleted":{}}""", code = 409)

        val result = ApiClient().push(
            base,
            "tok",
            LedgerPush(baseRev = 3, ledger = JsonObject(emptyMap())),
        )

        val recorded = server.takeRequest()
        assertEquals("PUT", recorded.method)
        assertTrue(result is PushResult.Conflict)
        assertEquals(7, (result as PushResult.Conflict).doc.rev)
        assertTrue(recorded.body.readUtf8().contains("\"baseRev\":3"))
    }

    @Test
    fun `推送成功时拿到新版本号`() = runBlocking {
        ok("""{"rev":4,"updatedAt":"2026-11-23T10:00:00Z","ledger":{"entries":[]},"deleted":{}}""")

        val result = ApiClient().push(
            base,
            "tok",
            LedgerPush(baseRev = 3, ledger = JsonObject(emptyMap())),
        )

        assertTrue(result is PushResult.Ok)
        assertEquals(4, (result as PushResult.Ok).doc.rev)
    }

    @Test
    fun `服务器报错时把 message 拿出来`() = runBlocking {
        ok("""{"error":"bad_credentials","message":"邮箱或密码不正确"}""", code = 401)

        val error = runCatching { ApiClient().login(base, "a@b.c", "wrong-password") }.exceptionOrNull()

        assertTrue(error is ApiException)
        assertEquals(401, (error as ApiException).status)
        assertEquals("邮箱或密码不正确", error.message)
    }

    /* ------------------------------ 版本 ------------------------------ */

    @Test
    fun `版本检查带上 versionCode，没发过版时 latest 是 null`() = runBlocking {
        ok("""{"latest":null,"hasUpdate":false}""")

        val result = ApiClient().latestRelease(base, 27)

        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/api/v1/app/latest?versionCode=27", recorded.path)
        assertNull(result.latest)
        assertFalse(result.hasUpdate)
    }

    @Test
    fun `版本检查读得出下载地址和 sha256`() = runBlocking {
        ok(
            """{"latest":{"id":"rel_1","versionCode":28,"versionName":"0.12.0","notes":"管","force":true,""" +
                """"size":12,"sha256":"abc","publishedAt":"2026-11-24T00:00:00Z",""" +
                """"downloadUrl":"https://api.example.com/api/v1/app/download/rel_1"},"hasUpdate":true}""",
        )

        val result = ApiClient().latestRelease(base, 27)

        assertEquals(28, result.latest?.versionCode)
        assertEquals("0.12.0", result.latest?.versionName)
        assertEquals("https://api.example.com/api/v1/app/download/rel_1", result.latest?.downloadUrl)
        assertTrue(result.latest?.force == true)
        assertTrue(result.hasUpdate)
    }

    @Test
    fun `下载安装包落到文件里，并算得出 sha256`(): Unit = runBlocking {
        // 比一个缓冲区大一点，顺便看看边界有没有漏字节
        val payload = ByteArray(64 * 1024 + 7) { (it % 251).toByte() }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("content-type", "application/vnd.android.package-archive")
                .setBody(Buffer().write(payload)),
        )

        val dir = Files.createTempDirectory("stockpot-apk").toFile()
        val target = File(dir, "stockpot-0.12.0.apk")
        var progress = 0f

        val sha = ApiClient().downloadApk(server.url("/api/v1/app/download/rel_1").toString(), target) {
            progress = it
        }

        assertEquals(payload.size.toLong(), target.length())
        assertEquals(payload.toList(), target.readBytes().toList())
        assertEquals(sha256(payload), sha)
        assertEquals(1f, progress, 0.001f)
        dir.deleteRecursively()
    }

    @Test
    fun `下载到空内容算失败，不会拿去安装`(): Unit = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))

        val dir = Files.createTempDirectory("stockpot-apk").toFile()
        val target = File(dir, "empty.apk")

        val error = runCatching {
            ApiClient().downloadApk(server.url("/api/v1/app/download/rel_1").toString(), target) { }
        }.exceptionOrNull()

        assertTrue(error is ApiException)
        dir.deleteRecursively()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
