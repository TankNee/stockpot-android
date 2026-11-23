package cn.neeto.stockpot

import android.app.Application
import cn.neeto.stockpot.core.data.LedgerStore
import cn.neeto.stockpot.core.settings.SettingsStore

class PotApp : Application() {

    lateinit var store: LedgerStore
        private set

    lateinit var settings: SettingsStore
        private set

    override fun onCreate() {
        super.onCreate()
        store = LedgerStore(this)
        settings = SettingsStore(this)
    }
}
