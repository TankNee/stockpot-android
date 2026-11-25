package cn.neeto.stockpot.core.net

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 同一时刻只跑一份，后来的等前一份的结果。
 *
 * 同步就是这个场景：开机 bootstrap、回到前台的补偿同步、编辑后 4 秒的推送、
 * 用户下拉刷新，四个入口随时会撞在一起。
 *
 * 以前这里是 `mutex.tryLock()`：抢不到锁就当失败返回 false。于是最常见的一幕是
 * 升级完打开应用，后台正在首轮同步，用户下拉一下，界面报「云端这次没对上」——
 * 其实云端那边同步得好好的，只是这次请求没排上队。
 *
 * 现在改成排队：后来的调用者直接 `await` 同一轮的结果，不再多打一遍网络，
 * 也不会把「正在同步」误报成「同步失败」。
 */
class SingleFlight<T> {

    /** 正在跑的那一轮。`null` 表示没人跑。 */
    private var flight: CompletableDeferred<Result<T>>? = null

    private val lock = Mutex()

    /** 现在有没有一轮在跑。只给测试和日志看，不参与决策。 */
    val running: Boolean
        get() = flight != null

    /**
     * 跑一轮。已经有一轮在跑的时候不重复执行，直接等那一轮的结果
     * （成功或失败都照实转发）。
     */
    suspend fun run(block: suspend () -> T): T {
        var owner = false
        val shared = lock.withLock {
            val current = flight ?: CompletableDeferred<Result<T>>().also {
                flight = it
                owner = true
            }
            current
        }
        if (!owner) return shared.await().getOrThrow()

        var outcome: Result<T> = Result.failure(Abandoned())
        try {
            outcome = Result.success(block())
        } catch (error: Throwable) {
            // 等的人不该跟着一起被取消：CancellationException 换成一条普通的失败，
            // 它们各自决定要不要重试；领跑的这一位照旧把取消抛出去
            outcome = Result.failure(if (error is CancellationException) Abandoned() else error)
            throw error
        } finally {
            // 先摘牌再唤醒：等的人醒来看到的是空位，下一轮由谁领跑重新决定
            lock.withLock { if (flight === shared) flight = null }
            shared.complete(outcome)
        }
        return outcome.getOrThrow()
    }

    /** 领跑的那一轮被取消了（页面退出、ViewModel 回收）。等的人拿到的就是它。 */
    class Abandoned : IllegalStateException("这一轮被中途取消了")
}
