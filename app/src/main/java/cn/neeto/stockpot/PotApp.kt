package cn.neeto.stockpot

import android.app.Application
import cn.neeto.stockpot.core.util.Diag
import cn.neeto.stockpot.core.data.LedgerStore
import cn.neeto.stockpot.core.net.ApiClient
import cn.neeto.stockpot.core.net.SyncManager
import cn.neeto.stockpot.core.net.SyncStateStore
import cn.neeto.stockpot.core.net.Updater
import cn.neeto.stockpot.core.settings.AppIcon
import cn.neeto.stockpot.core.settings.AuthStore
import cn.neeto.stockpot.core.settings.SettingsStore
import cn.neeto.stockpot.widget.WidgetRefresh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PotApp : Application() {

    lateinit var store: LedgerStore
        private set

    lateinit var settings: SettingsStore
        private set

    lateinit var auth: AuthStore
        private set

    lateinit var sync: SyncManager
        private set

    lateinit var updater: Updater
        private set

    /** 刷桌面小组件用。不跟界面走，App 在后台改了账也要让卡片跟上。 */
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var widgetJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        // 最先装上：后面任何一步崩了都能留下堆栈
        Diag.install(this)
        store = LedgerStore(this)
        // 导出诊断时补一句规模，判断是不是数据量的问题
        Diag.stats = {
            val data = store.data.value
            "分类 ${data.categories.size} / 规则 ${data.rules.size} / 条目 ${data.entries.size} / 账户 ${data.accounts.size}"
        }
        settings = SettingsStore(this)
        auth = AuthStore(this)
        val api = ApiClient()
        sync = SyncManager(
            api = api,
            store = store,
            auth = auth,
            settings = settings,
            state = SyncStateStore(this),
        )
        updater = Updater(this, api, settings)
        // 本地一改就告诉同步那边；没登录时它自己会忽略
        store.onLocalChange = sync::onLocalChange
        sync.start()

        // 图标入口和设置对齐。系统偶尔会把组件状态重置，桌面图标就跟设置里的选择对不上；
        // reconcile 内部会先比对，已经一致就直接返回，不会每次都惊动桌面重建图标缓存。
        val saved = settings.state.value
        AppIcon.reconcile(this, saved.iconStyle, saved.iconMask)

        // 开机顺手问一句有没有新版：失败不出声，设置页里照旧显示「还没检查过」
        updater.check(silent = true)

        // 小组件跟着账本走：落盘那边有 180ms 防抖，这里再等一会儿，
        // 一批改动合完了只重画一次，不是为了省电，是为了别让桌面抖个不停
        background.launch {
            store.data.collect {
                widgetJob?.cancel()
                widgetJob = background.launch {
                    delay(1200)
                    WidgetRefresh.all(this@PotApp)
                }
            }
        }
    }
}
