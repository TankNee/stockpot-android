# Stockpot Mobile

桌面记账软件 [Stockpot](../stockpot) 的安卓客户端。原生 Kotlin + Jetpack Compose，
**本地优先**：没有账号、没有同步、没有网络请求，账本是一份可读的 JSON，存在应用私有目录里。

界面语言、配色和账本口径都跟桌面端一致，`ledger.json` 双向互通：
手机导出的账本可以直接丢给桌面端打开，桌面端的账本也能合并进手机。

## 功能

- **总览** — 月/年两个视角：支出、收入、结余、日均、笔数；每日支出柱状图；分类环形图与排行；
  月度日历（格子按当天花销着色）；最近记录；资产卡。
- **流水** — 按月列出，按日分组并显示当日收支；按类型筛选、关键词搜索、按某一天过滤；
  点任意一行编辑或删除，删除有 3.6 秒撤销。
- **记一笔** — 大号金额输入、支出/收入/转账、分类树选择（可现场新建）、日期快捷选项与日历、
  商家、账户、备注、项目归属。
- **项目** — 把一次旅行、搬家、装修的账目收在一起。项目只是分组，不改变分类和金额；
  删除项目只解除分组，账目一条不删。可从项目页批量「添加账目」。
- **导入账单** — 微信 `.xlsx`、支付宝 `.csv`、iCost、通用 CSV、桌面端 `ledger.json`；
  导入前先预览（新增/重复/忽略、收支合计、日期范围、样例行），确认后才写入。
  也支持在微信/支付宝里直接「分享到 Stockpot」。
- **资产** — 手工维护的银行卡、零钱、信用卡余额，与账本累计净一起看。
- **设置** — 主题（跟随系统/浅/深）、分类树管理、关键词规则管理、导出/导入账本、示例数据、清空流水。

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
  ui/PotRoot.kt             底部导航、记一笔入口、撤销条
  ui/PotViewModel.kt        状态与所有写操作
  ui/theme/Theme.kt         暖纸/墨色配色与排版
  ui/components/            卡片、分段控件、日历、环形图、流水行
  ui/screens/               总览 / 流水 / 项目 / 导入 / 设置 / 编辑 / 钱包
app/src/test/java/…         20 个单元测试：解析、去重、退款、汇总、分类改名、JSON 兼容
```

## 构建

```bash
./gradlew :app:assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest  # 单元测试
```

`local.properties` 里的 `sdk.dir` 指向本机 Android SDK。release 构建需要自备签名配置。

## 与桌面端的差别

桌面端还有股票行情、菜单栏读数、CLI 和 AI 分类。手机端**只做记账**：
不联网取行情，不接 CLI/MCP，不调用模型分类。导入、归类、汇总全在本机完成。
