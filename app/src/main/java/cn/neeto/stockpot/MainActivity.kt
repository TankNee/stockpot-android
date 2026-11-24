package cn.neeto.stockpot

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import cn.neeto.stockpot.core.util.Documents
import cn.neeto.stockpot.ui.PotRoot
import cn.neeto.stockpot.ui.PotViewModel
import cn.neeto.stockpot.ui.theme.PotTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val viewModel: PotViewModel by viewModels()

    /** 从别的 App 分享进来的账单，交给导入页解析。 */
    private var sharedFile by mutableStateOf<Documents.Picked?>(null)

    /** 长按桌面图标点进来的那一下。消费掉就清空，切 tab 回来不该再执行一次。 */
    private var launchAction by mutableStateOf<LaunchAction?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readSharedIntent(intent)
        readLaunchAction(intent)
        setContent {
            val prefs by viewModel.prefs.collectAsStateWithLifecycle()
            PotTheme(prefs) {
                PotRoot(
                    vm = viewModel,
                    incomingFile = sharedFile,
                    onIncomingConsumed = { sharedFile = null },
                    launchAction = launchAction,
                    onLaunchConsumed = { launchAction = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readSharedIntent(intent)
        readLaunchAction(intent)
    }

    override fun onStart() {
        super.onStart()
        // 回到前台顺手对一次账（没登录时是空操作）
        viewModel.syncOnForeground()
    }

    override fun onStop() {
        super.onStop()
        viewModel.flush()
    }

    private fun readLaunchAction(intent: Intent?) {
        val name = AppShortcuts.of(intent?.action) ?: return
        launchAction = LaunchAction(name, System.currentTimeMillis())
    }

    private fun readSharedIntent(intent: Intent?) {
        if (intent == null) return
        val uri = when (intent.action) {
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            Intent.ACTION_VIEW -> intent.data
            else -> null
        } ?: return
        // 账单可能有几 MB，别在主线程里读
        lifecycleScope.launch {
            val picked = withContext(Dispatchers.IO) {
                runCatching { Documents.read(this@MainActivity, uri) }.getOrNull()
            }
            if (picked != null) sharedFile = picked
        }
    }
}
