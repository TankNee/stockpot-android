# Stockpot Mobile

桌面记账软件 [Stockpot](../stockpot) 的安卓客户端。原生 Kotlin + Jetpack Compose，
**本地优先**：没有账号、没有同步、没有网络请求，账本是一份可读的 JSON，存在应用私有目录里。

界面语言、配色和账本口径都跟桌面端一致，`ledger.json` 双向互通：
手机导出的账本可以直接丢给桌面端打开，桌面端的账本也能合并进手机。

## 功能

底部四个 tab：**总览 / 流水 / 资产 / 设置**。

- **总览** — 月/年两个视角：支出、收入、结余、日均、笔数；每日支出柱状图；分类环形图与排行；
  月度日历（格子按当天花销着色）；最近记录；底部是**项目入口**，露最近三个项目，点「全部」进项目页。
- **流水** — 按月列出，按日分组并显示当日收支；类型筛选、**账户筛选**（只看某张卡）、
  关键词搜索、按某一天过滤，排序可切「按时间 / 金额高低」；点任意一行编辑或删除，
  删除有 3.6 秒撤销。
- **资产** — 每个账户的余额（储蓄卡、零钱、信用卡、投资、公积金、医保……）：净资产、总资产、
  总负债，一条按类型的资产构成色带，下面按类型分组列出账户。点账户进编辑页。
  添加账户分两步：先选类型，再填名称、备注、余额；「计入总资产」关掉后余额只留在列表里，
  「记账时可被选择」决定它是否出现在记一笔的账户候选里。
  余额是手工维护的快照，不会跟着账目自动变。
  类型页会把**导入账单里识别出来、但还没建成账户的名字**列在外面，点一下就连名字带类型建好，
  账户名会直接填进表单；类型也可以自己起（比如「数字钱包」），自定义类型直接参与分组和统计。
- **记一笔** — 大号金额输入、支出/收入/转账、分类树选择（可现场新建）、日期快捷选项与日历、
  商家、账户（**从列表里选**，也能现场新建）、备注、项目归属。

表单、选择器、确认框统一是**底部弹层**（`PotSheet`）：从下面推上来、键盘不挡输入框，
全应用没有 AlertDialog 了（日期选择也放在弹层里）。
- **设置** — 主题（跟随系统/浅/深）、分类树管理、关键词规则管理、**导入账单**、导出/导入账本、
  示例数据、清空流水。

**股票** 也走子页面：总览里的股票卡 → 股票总览（市值、今日/持仓盈亏）→ 某个券商账户 → 单只票详情。
可以建多个券商账户，每个账户里多只票（成本价 + 股数），A 股 / 港股 / 美股都能记。

- 总览的股票卡带一条**近一个月市值 sparkline**，下面写区间涨跌（金额 + 百分比）
- 账户页有一张**总资产走势**卡：1 个月 / 3 个月 / 1 年，按持仓 × 每日收盘价算，给出区间盈亏
  的绝对值和百分比，虚线是区间起点
- 点某只票进详情：**当日分时**（按住看某一分钟的价格和涨跌）和**日 K**（前复权，最近 90 根，
  涨红跌绿），下面是这笔持仓的股数、成本价、市值、盈亏和收益率
- 行情走腾讯 `qt.gtimg.cn`（GBK，无需 key），分时与日 K 走 `web.ifzq.gtimg.cn`；
  进页面自动拉一次、也能手点刷新；拉不到就按成本算，不编造价格和曲线
- 股票市值计入资产页的净资产和「资产构成」，作为一个独立的账户分组

**项目** 不再是 tab，而是首页推进去的子页面（带返回）：总览底部卡片 →「全部」→ 项目列表 →
项目详情。项目只是分组，不改变分类和金额；删除项目只解除分组，账目一条不删。
项目详情里可以批量「添加账目」。

**导入** 同样不是 tab，是设置里的入口（也是一层可返回的页面）：微信 `.xlsx`、支付宝 `.csv`、
iCost、通用 CSV、桌面端 `ledger.json`。导入前先预览（新增/重复/忽略、收支合计、日期范围、样例行），
确认后才写入。在微信/支付宝里直接「分享到 Stockpot」也会落到这个页面。

金额一律以 **分**（`Long`）存储，汇总不会漂移。支出用红、收入用绿（国内习惯）。

## 支持的账单

| 来源 | 格式 | 说明 |
| --- | --- | --- |
| 微信支付 | `.xlsx` | 按表头 `交易时间/交易对方/商品/收/支/金额(元)` 识别，前几行说明自动跳过 |
| 支付宝 | `.csv`（通常 GBK） | 两代表头都认；`付款时间` 优先于 `交易创建时间` |
| iCost | `.xlsx` / `.csv` | 保留 `一级分类/二级分类` 两级分类 |
| 通用 CSV | `.csv` | 日期 + 金额即可；有 `支出金额`/`收入金额` 分列也能读，银行流水多能进来 |
| Stockpot 账本 | `.json` | 按 id + 去重键合并，重复导入不产生重复账目 |

解析规则（与桌面端一致）：

- **按列名找列，从不按位置**——用户会改文件名，平台也会改格式。扩展名不作数，靠字节判断是不是 zip。
- 微信的 `交易时间` 可能是 Excel 日期序列号（`46348.4575`），会换算成 `2026-11-22 10:58`。
- 支付宝的 GBK 用 GB18030（GBK 超集）解码；UTF-8 先试，失败再退回。
- `交易关闭/失败/已撤销/已取消` 的行丢弃；`退款` 的行保留。
- 微信会改写原消费行的状态（`已全额退款`），直接记在原行上；支付宝的独立退款行按
  「同平台、45 天内、余额够、金额尽量相等」匹配到原始消费，只留下净成本。
- `/` 和 `不计收支` 视为账户互转，可见但不计入收支；余额宝/零钱通的「收益发放」算收入。
- 去重键：有交易单号用单号，没有就用 `来源:日期:金额:对方`。
- 分类：先按关键词规则（最长命中胜出，方向不符的规则跳过），再看平台自己的分类标签，
  最后才猜；猜不中就归「其他」。

## 数据

```jsonc
{
  "entries": [
    {
      "id": "…", "date": "2026-11-22", "time": "12:20",
      "kind": "expense", "amount": 1850, "category": "餐饮/午餐",
      "account": "微信零钱", "counterparty": "楼下小馆", "note": "",
      "source": "wechat", "externalId": "4200001…",
      "excluded": false, "archived": false,
      "refundedAmount": 0, "refundedAt": "",
      "categoryLocked": false, "projectId": "", "createdAt": "2026-11-22T04:20:11Z"
    }
  ],
  "categories": [{ "name": "午餐", "parent": "餐饮", "kind": "expense", "icon": "food" }],
  "accounts": ["微信零钱"],
  "imports": [{ "id": "…", "file": "微信支付账单.xlsx", "source": "wechat", "added": 128, "skipped": 3 }],
  "wallets": [{ "id": "…", "name": "招商银行", "kind": "debit", "balance": 4286500 }],
  "projects": [{ "id": "…", "name": "杭州出差", "startDate": "2026-11-12", "endDate": "2026-11-14" }],
  "rules": [{ "id": "seed-0", "pattern": "星巴克", "category": "餐饮/咖啡" }],
  "ignoredDuplicates": [],
  "ignoredRepayments": [],
  "schemaVersion": 4
}
```

字段名与桌面端 `ledger.json` 完全一致（camelCase），老版本里 `categories` 是纯字符串数组，
两种都能读。

落盘方式：`filesDir/ledger.json`，先写 `ledger.json.tmp` 再原子 rename，另存一份
`ledger.bak.json`；启动时读不出来会先试着读备份，并把坏文件留成 `ledger.corrupt.json`。
编辑后 180ms 防抖写盘，切到后台立刻 flush。

## 技术栈与结构

Kotlin 2.1 / Compose（BOM 2024.12）/ Material 3 / kotlinx.serialization / minSdk 26。
没有数据库、没有 DI 框架、没有导航库——单 Activity + 一个状态源，够用且启动快。

```
app/src/main/java/cn/neeto/stockpot/
  PotApp.kt                 Application：持有 store 与 settings
  MainActivity.kt           单 Activity，处理系统分享进来的文件
  core/model/Ledger.kt      账本模型、默认分类树与规则、老格式兼容
  core/data/LedgerMath.kt   金额/日期格式化、月汇总、每日趋势、年度视图
  core/data/LedgerOps.kt    所有编辑操作的纯函数（新增/改/删/分类/规则/项目/钱包/示例数据）
  core/data/LedgerStore.kt  JSON 落盘：原子写、备份、损坏恢复
  core/importer/XlsxReader.kt  极简 xlsx 读取（zip + XmlPullParser，不引 POI）
  core/importer/BillParser.kt  微信/支付宝/iCost/通用 CSV 的行解析
  core/importer/ImportEngine.kt 预览、去重、退款匹配、关键词归类、账本合并
  core/settings/SettingsStore.kt 主题偏好
  core/util/Documents.kt    SAF 文档读写
  ui/PotRoot.kt             四个 tab 的路由、叠加页层级与切换动画、记一笔入口、撤销条
  ui/PotViewModel.kt        状态与所有写操作
  ui/theme/Theme.kt         暖纸/墨色配色与排版
  ui/components/            卡片、分段控件、日历、环形图、流水行
  ui/screens/               总览 / 流水 / 资产 / 设置 / 项目列表与详情 / 导入页 / 记一笔
app/src/test/java/…         24 个单元测试：解析、去重、退款、汇总、分类改名、JSON 兼容、日期换算、金额格式
```

## 构建

```bash
./gradlew :app:assembleRelease    # app/build/outputs/apk/release/app-release.apk（已签名、R8）
./gradlew :app:assembleDebug      # app/build/outputs/apk/debug/app-debug.apk（包名带 .debug）
./gradlew :app:testDebugUnitTest  # 单元测试
```

`local.properties` 里的 `sdk.dir` 指向本机 Android SDK。

签名配置在 `keystore.properties`，key 在 `keystore/stockpot-release.jks`（密码 `stockpot2026`，
alias `stockpot`）。这两个文件跟着仓库走：**同一个 key 才能覆盖安装**，删了或换机器生成新 key，
已经装在手机上的版本就只能卸载重装。

release 开了 R8（包体约 1.5 MB）。`proguard-rules.pro` 里保留了 kotlinx.serialization 的
`$$serializer` 和模型字段名——那是 `ledger.json` 的 key，不能被混淆掉。

从更早的 debug 包换到 release 时签名不同，需要先在设置里「导出账本」，卸载旧包，装好后再导入。

## 字号与热区

0.3.0 把整套排印往上抬了一档：正文 15sp、说明 13sp、小标签 12sp，金额 17sp 起，
标题 22sp。分类管理和关键词规则两个弹层的行高、删除按钮都放大到 40dp 以上，
设置项纵向留白加到 15dp——一列能点的地方基本都是拇指能稳稳按住的尺寸。

## 性能上做的几件事

列表滑动卡顿基本都来自「每帧都在做重复的工作」，这一版清掉了这些：

- 金额格式不再每次 `new DecimalFormat` + `BigDecimal`，改手写千分位拼接；`shortDate` 用字符串切片，
  `dayLabel` / `weekday` 带缓存。一屏几十行金额、42 格日历不再各建一个 formatter。
- 日历 42 个格子共用一个 `pointerInput`，不再每格挂一个 `Modifier.clickable`（省掉 42 个 ripple 节点）。
- 柱状图、环形图的比例、颜色、刻度文字都在 `remember` 里算好，绘制阶段只画。
- 卡片默认只画圆角背景，不再 `clip()` 建图层；只有可点的卡片才 clip（为了 ripple 边界）。
- 账本模型打了 `@Immutable`，账本没变时列表项可以跳过重组；`LazyColumn` 加了 key 与 contentType。

## 与桌面端的差别

桌面端还有股票行情、菜单栏读数、CLI 和 AI 分类。手机端**只做记账**：
不联网取行情，不接 CLI/MCP，不调用模型分类。导入、归类、汇总全在本机完成。
