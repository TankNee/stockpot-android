package cn.neeto.stockpot.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cn.neeto.stockpot.ui.theme.LocalPotColors

/**
 * 下拉刷新，把 material3 那个转圈换成自家配色。
 *
 * 默认指示器取的是 M3 色板里的 surfaceContainerHigh，在这套暖纸配色上会跳出来一块灰白；
 * 这里改成面板色打底、品牌色转圈，落在页面上看不出来是个「外来控件」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PotPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalPotColors.current
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = colors.panel,
                color = colors.brand,
            )
        },
        content = content,
    )
}
