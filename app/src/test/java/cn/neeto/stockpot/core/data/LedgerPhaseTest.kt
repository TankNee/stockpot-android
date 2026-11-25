package cn.neeto.stockpot.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 冷启动那一小段时间里，界面到底该不该说「账本还是空的」。
 *
 * 这条判断以前根本不存在（只有「有数据」和「没数据」），结果第一帧就摆出了
 * 「记一笔试试」，用户以为账丢了。
 */
class LedgerPhaseTest {

    @Test
    fun `库还没读完时不下结论`() {
        assertEquals(LedgerPhase.LOADING, ledgerPhase(ready = false, entries = 0, signedIn = false, syncing = false))
        // 读库途中内存里可能已经有几笔（读库期间记的），但只要还没读完，就不算数
        assertEquals(LedgerPhase.LOADING, ledgerPhase(ready = false, entries = 3, signedIn = true, syncing = false))
    }

    @Test
    fun `有账目就直接画`() {
        assertEquals(LedgerPhase.READY, ledgerPhase(ready = true, entries = 1, signedIn = false, syncing = false))
        // 同步中也不该退回骨架：数据已经在手上，刷新是后台的事
        assertEquals(LedgerPhase.READY, ledgerPhase(ready = true, entries = 1_000, signedIn = true, syncing = true))
    }

    @Test
    fun `登录后本机空、正在拉云端时先等一等`() {
        assertEquals(LedgerPhase.LOADING, ledgerPhase(ready = true, entries = 0, signedIn = true, syncing = true))
    }

    @Test
    fun `对完账还是空，才算真空`() {
        assertEquals(LedgerPhase.EMPTY, ledgerPhase(ready = true, entries = 0, signedIn = true, syncing = false))
    }

    @Test
    fun `没登录的空就是真的空`() {
        assertEquals(LedgerPhase.EMPTY, ledgerPhase(ready = true, entries = 0, signedIn = false, syncing = false))
    }
}
