#!/usr/bin/env python3
"""把覆盖率 XML 和压测报告拼成一段 Markdown，CI 直接贴进 job summary。

为什么分开算两个数字：Compose 界面在本机 JVM 单测里根本跑不起来（要真机或
Robolectric），把它们算进去，整体覆盖率会低得没有参考价值。所以
「核心逻辑」只统计 `core/` 和 `ui/` 里那几个纯逻辑文件，
「全仓」照实报，两个数一起看才不会互相误导。

用法：
    python3 scripts/ci-summary.py coverage.xml ledger-perf.md
"""

import os
import sys
import xml.etree.ElementTree as ET

# 这些包是纯逻辑，单测能覆盖，把它们单独算一份
CORE_PACKAGES = ("cn/neeto/stockpot/core/",)
# ui 包里只有这两块有 JVM 单测（ViewModel 的下拉刷新逻辑、刷新提示文案）
CORE_UI_CLASSES = ("PotViewModel", "RefreshNote")


def counter(element, kind):
    for item in element.findall("counter"):
        if item.get("type") == kind:
            return int(item.get("missed")), int(item.get("covered"))
    return 0, 0


def percent(missed, covered):
    total = missed + covered
    return (covered * 100.0 / total) if total else 0.0


def main():
    if len(sys.argv) < 2:
        print("用法：ci-summary.py <coverage.xml> [perf.md]", file=sys.stderr)
        return 2
    xml_path = sys.argv[1]
    perf_path = sys.argv[2] if len(sys.argv) > 2 else ""

    lines = []

    if perf_path and os.path.exists(perf_path):
        with open(perf_path, encoding="utf-8") as handle:
            body = handle.read().strip().splitlines()
        lines.append("## 账本压测")
        lines.append("")
        # 报告自带一级标题和说明，去掉前四行直接贴表格
        lines.extend(body[4:])
        lines.append("")
    else:
        lines.append("## 账本压测")
        lines.append("")
        lines.append("这次没跑出压测报告，看单元测试那一步的日志。")
        lines.append("")

    lines.append("## 覆盖率")
    lines.append("")
    if os.path.exists(xml_path):
        root = ET.parse(xml_path).getroot()
        missed, covered = counter(root, "LINE")
        lines.append(f"- 全仓行覆盖率：**{percent(missed, covered):.1f}%**（{covered}/{missed + covered}）")

        core_missed = core_covered = 0
        for package in root.findall("package"):
            name = package.get("name", "")
            if name.startswith(CORE_PACKAGES):
                p_missed, p_covered = counter(package, "LINE")
                core_missed += p_missed
                core_covered += p_covered
            elif name == "cn/neeto/stockpot/ui":
                for clazz in package.findall("class"):
                    simple = clazz.get("name", "").rsplit("/", 1)[-1]
                    if simple in CORE_UI_CLASSES:
                        c_missed, c_covered = counter(clazz, "LINE")
                        core_missed += c_missed
                        core_covered += c_covered
        lines.append(
            f"- 核心逻辑行覆盖率（core/ + 同步与刷新逻辑）："
            f"**{percent(core_missed, core_covered):.1f}%**（{core_covered}/{core_missed + core_covered}）"
        )
        branch_missed, branch_covered = counter(root, "BRANCH")
        lines.append(f"- 分支覆盖率：**{percent(branch_missed, branch_covered):.1f}%**")
        lines.append("")
        lines.append("<sub>Compose 界面要真机才有数字，没算进「核心逻辑」。</sub>")
    else:
        lines.append("没有覆盖率的 XML，检查 coverageReport 那一步。")

    print("\n".join(lines))
    return 0


if __name__ == "__main__":
    sys.exit(main())
