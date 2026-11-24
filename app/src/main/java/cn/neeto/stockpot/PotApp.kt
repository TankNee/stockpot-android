package cn.neeto.stockpot

import android.app.Application
import cn.neeto.stockpot.core.data.LedgerStore
import cn.neeto.stockpot.core.net.ApiClient
import cn.neeto.stockpot.core.net.SyncManager
import cn.neeto.stockpot.core.net.SyncStateStore
import cn.neeto.stockpot.core.net.Updater
import cn.neeto.stockpot.core.settings.AppIcon
import cn.neeto.stockpot.core.settings.AuthStore
import cn.neeto.stockpot.core.settings.SettingsStore

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

    override fun onCreate() {
        super.onCreate()
        store = LedgerStore(this)
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
    }
}
