package cn.neeto.stockpot.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Attractions
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.DirectionsTransit
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.House
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LocalBar
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalDining
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.LocalParking
import androidx.compose.material.icons.outlined.LocalTaxi
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.TakeoutDining
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.neeto.stockpot.ui.theme.LocalPotColors

/**
 * 分类图标：一套和桌面端同名的 key，手机端映射到 Material 的 outlined 系列。
 *
 * 只用 outlined（单线条、24 网格），和界面里其它图标同一风格；不用 emoji——
 * emoji 每个系统画得都不一样，放进这套配色里也花。
 *
 * key 名与桌面端 `src/ledger/categories.tsx` 里那份保持一致，同一个账本同步
 * 过来两端能认出同一个图标。
 */
private val ICONS: Map<String, ImageVector> = linkedMapOf(
    // 吃
    "food" to Icons.Outlined.Restaurant,
    "coffee" to Icons.Outlined.LocalCafe,
    "takeout" to Icons.Outlined.TakeoutDining,
    "snack" to Icons.Outlined.Cookie,
    "drink" to Icons.Outlined.LocalBar,
    "salad" to Icons.Outlined.LocalDining,
    // 行
    "transport" to Icons.Outlined.DirectionsTransit,
    "bus" to Icons.Outlined.DirectionsBus,
    "taxi" to Icons.Outlined.LocalTaxi,
    "fuel" to Icons.Outlined.LocalGasStation,
    "parking" to Icons.Outlined.LocalParking,
    "bike" to Icons.Outlined.DirectionsBike,
    // 买
    "shopping" to Icons.Outlined.ShoppingBasket,
    "basket" to Icons.Outlined.ShoppingCart,
    "shirt" to Icons.Outlined.Checkroom,
    "device" to Icons.Outlined.Devices,
    "beauty" to Icons.Outlined.Brush,
    // 住
    "home" to Icons.Outlined.House,
    "rent" to Icons.Outlined.Key,
    "bolt" to Icons.Outlined.Bolt,
    "repair" to Icons.Outlined.Build,
    // 生活
    "fun" to Icons.Outlined.Attractions,
    "film" to Icons.Outlined.Movie,
    "game" to Icons.Outlined.SportsEsports,
    "travel" to Icons.Outlined.Flight,
    "pet" to Icons.Outlined.Pets,
    "kid" to Icons.Outlined.ChildCare,
    // 健康
    "health" to Icons.Outlined.MonitorHeart,
    "pill" to Icons.Outlined.Medication,
    "gym" to Icons.Outlined.FitnessCenter,
    // 成长
    "book" to Icons.Outlined.MenuBook,
    "school" to Icons.Outlined.School,
    // 通讯
    "phone" to Icons.Outlined.Phone,
    "net" to Icons.Outlined.Wifi,
    // 人情
    "gift" to Icons.Outlined.CardGiftcard,
    "redpacket" to Icons.Outlined.Redeem,
    // 钱
    "salary" to Icons.Outlined.Payments,
    "bonus" to Icons.Outlined.EmojiEvents,
    "invest" to Icons.Outlined.TrendingUp,
    "refund" to Icons.Outlined.Refresh,
    "transfer" to Icons.Outlined.SwapHoriz,
    "wallet" to Icons.Outlined.AccountBalanceWallet,
    // 兜底
    "dots" to Icons.Outlined.MoreHoriz,
)

private val LABELS: Map<String, String> = mapOf(
    "food" to "餐饮", "coffee" to "咖啡", "takeout" to "外卖", "snack" to "零食",
    "drink" to "酒水", "salad" to "轻食",
    "transport" to "交通", "bus" to "公交", "taxi" to "打车", "fuel" to "加油",
    "parking" to "停车", "bike" to "骑行",
    "shopping" to "购物", "basket" to "日用", "shirt" to "服饰", "device" to "数码",
    "beauty" to "美妆",
    "home" to "居住", "rent" to "房租", "bolt" to "水电", "repair" to "维修",
    "fun" to "娱乐", "film" to "电影", "game" to "游戏", "travel" to "旅行",
    "pet" to "宠物", "kid" to "孩子",
    "health" to "医疗", "pill" to "药品", "gym" to "健身",
    "book" to "教育", "school" to "学习",
    "phone" to "通讯", "net" to "网络",
    "gift" to "人情", "redpacket" to "红包",
    "salary" to "工资", "bonus" to "奖金", "invest" to "理财", "refund" to "退款",
    "transfer" to "转账", "wallet" to "账户",
    "dots" to "其他",
)

val CATEGORY_ICON_KEYS: List<String> = ICONS.keys.toList()

/**
 * 一条分类实际该画哪个图标：自己配了就用，没配按名字猜。
 * 老账本里 icon 基本都空着，靠这层兜底才不会满屏一个样。
 */
fun cn.neeto.stockpot.core.model.CategoryDef.effectiveIcon(): String =
    icon.ifEmpty { cn.neeto.stockpot.core.model.builtinIcon(name) }

/** 未知 key 返回 null，界面自己决定退回文字。 */
fun categoryIcon(key: String): ImageVector? = ICONS[key]

fun categoryIconLabel(key: String): String = LABELS[key] ?: ""

/**
 * 分类路径 → 图标 key。列表行（EntryRow / CategoryAvatar）不直接拿得到账本，
 * 由根节点统一提供一份，查一下就行。
 */
val LocalCategoryIcons = compositionLocalOf<Map<String, String>> { emptyMap() }

/** 有图标就画图标，没有就不占位——调用方自己决定要不要画别的东西。 */
@androidx.compose.runtime.Composable
fun CategoryGlyph(
    key: String,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    tint: Color = Color.Unspecified,
) {
    val vector = categoryIcon(key) ?: return
    Icon(
        imageVector = vector,
        contentDescription = null,
        tint = if (tint == Color.Unspecified) LocalPotColors.current.ink2 else tint,
        modifier = modifier.size(size),
    )
}

/**
 * 图标选择网格。8 列铺满一行，43 个图标 6 行放下，不用滚动。
 * 选中那格用 brand 描边 + brandTint 底，和界面的其它选中态一致。
 */
@androidx.compose.runtime.Composable
fun CategoryIconGrid(
    selected: String,
    onSelect: (String) -> Unit,
    columns: Int = 8,
) {
    val colors = LocalPotColors.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CATEGORY_ICON_KEYS.chunked(columns).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.forEach { key ->
                    val active = key == selected
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (active) colors.brandTint else Color.Transparent)
                            .border(
                                1.dp,
                                if (active) colors.brand.copy(alpha = 0.65f) else Color.Transparent,
                                RoundedCornerShape(10.dp),
                            )
                            .clickable { onSelect(key) }
                            .padding(vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val tint = if (active) colors.brandInk else colors.ink2
                        Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                            CategoryGlyph(key, size = 18.dp, tint = tint)
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = categoryIconLabel(key),
                            fontSize = 9.sp,
                            color = if (active) colors.brandInk else colors.ink3,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                // 最后一行不满时补空位，免得几格被拉宽
                repeat(columns - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
