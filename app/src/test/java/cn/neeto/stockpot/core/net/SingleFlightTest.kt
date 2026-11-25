package cn.neeto.stockpot.core.net

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SingleFlightTest {

    @Test
    fun `同时进来的三次只跑一份，结果都一样`() = runTest {
        val flight = SingleFlight<Int>()
        var runs = 0
        val gate = CompletableDeferred<Unit>()

        val calls = List(3) {
            async {
                flight.run {
                    runs++
                    gate.await()
                    runs
                }
            }
        }
        // 让三个协程都挂在 await 上，再一起放行
        testScheduler.advanceUntilIdle()
        gate.complete(Unit)

        val results = calls.map { it.await() }
        assertEquals("只该真正执行一次", 1, runs)
        assertEquals(listOf(1, 1, 1), results)
    }

    @Test
    fun `后到的不抢跑，等前一轮的真实结果`() = runTest {
        val flight = SingleFlight<Boolean>()
        val release = CompletableDeferred<Unit>()
        var runs = 0

        val first = async {
            flight.run {
                runs++
                release.await()
                true
            }
        }
        testScheduler.advanceUntilIdle()

        // 第二轮进来的时候第一轮还没完：它必须排队，而不是自己再打一遍网络
        val second = async { flight.run { runs++; false } }
        testScheduler.advanceUntilIdle()
        release.complete(Unit)

        assertTrue("两个调用者拿到的是同一轮的结果", first.await() && second.await())
        assertEquals(1, runs)
    }

    @Test
    fun `失败会原样转给等待的人`() = runTest {
        val flight = SingleFlight<String>()
        val release = CompletableDeferred<Unit>()

        val first = async { runCatching { flight.run { release.await(); error("断网了") } } }
        testScheduler.advanceUntilIdle()
        val second = async { runCatching { flight.run { "不该跑到这里" } } }
        testScheduler.advanceUntilIdle()
        release.complete(Unit)

        assertEquals("断网了", first.await().exceptionOrNull()?.message)
        assertEquals("断网了", second.await().exceptionOrNull()?.message)
    }

    @Test
    fun `领跑被取消时等待者不会被一起取消，之后还能再跑一轮`() = runTest {
        val flight = SingleFlight<String>()
        val started = CompletableDeferred<Unit>()

        val owner = launch {
            flight.run {
                started.complete(Unit)
                awaitCancellation()
            }
        }
        testScheduler.advanceUntilIdle()

        val waiter = async { runCatching { flight.run { "second" } } }
        testScheduler.advanceUntilIdle()

        owner.cancelAndJoin()
        val failure = waiter.await().exceptionOrNull()
        assertTrue("等待者拿到的是一条普通失败，不是取消", failure is SingleFlight.Abandoned)

        // 摘牌干净了：下一轮照常开张
        assertEquals("after", flight.run { "after" })
    }

    @Test
    fun `一轮结束后可以继续跑下一轮`() = runTest {
        val flight = SingleFlight<Int>()
        var runs = 0
        repeat(3) {
            assertEquals(++runs, flight.run { runs })
        }
    }
}
