// 深色纸样的对比度体检：WCAG 2.x 相对亮度公式，正文 4.5、大字/图形 3.0。
// 用法：node scripts/dark-contrast.mjs
//
// 色值直接从 Theme.kt 里读，不在这里抄第二份 —— 抄一份就会漂：
// 改了 Kotlin 忘了改脚本，体检结果比不体检更危险。

import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const themePath = join(here, '..', 'app/src/main/java/cn/neeto/stockpot/ui/theme/Theme.kt');
const source = readFileSync(themePath, 'utf8');

/** 从 Theme.kt 里捞出每套深色纸：canvas / panel / ink ... */
function readDarkPalettes(text) {
  const out = {};
  const block = /private val (\w+)Dark = PotColors\(([\s\S]*?)\n\)/g;
  let m;
  while ((m = block.exec(text))) {
    const [, name, body] = m;
    const colors = {};
    const field = /(\w+)\s*=\s*Color\(0x[Ff]{2}([0-9A-Fa-f]{6})\)/g;
    let f;
    while ((f = field.exec(body))) colors[f[1]] = '#' + f[2].toUpperCase();
    if (colors.canvas) out[name] = colors;
  }
  return out;
}

// 0.19.x 的基线，只用来做「改前」对照，不进代码
const BEFORE = {
  canvas: '#14130F', panel: '#1D1C17', panel2: '#26241D',
  line: '#302E24', lineStrong: '#3F3C2F',
  ink: '#F4F1E7', ink2: '#BFB9AA', ink3: '#948E7D',
  brand: '#D6BE6D', brandInk: '#EFDDA2', brandTint: '#2A2513',
  expense: '#E6917E', income: '#97C29B',
};

const hex2rgb = (h) => {
  const s = h.replace('#', '');
  return [0, 2, 4].map((i) => parseInt(s.slice(i, i + 2), 16));
};
const lin = (c) => {
  const v = c / 255;
  return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
};
const lum = (h) => {
  const [r, g, b] = hex2rgb(h).map(lin);
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
};
const ratio = (a, b) => {
  const [x, y] = [lum(a), lum(b)].sort((m, n) => n - m);
  return (x + 0.05) / (y + 0.05);
};
/** 半透明前景压在背景上的实际颜色（四舍五入到整数字节，跟渲染一致） */
const alpha = (fg, bg, a) => {
  const f = hex2rgb(fg);
  const b = hex2rgb(bg);
  return (
    '#' +
    f.map((v, i) => Math.round(v * a + b[i] * (1 - a)).toString(16).padStart(2, '0')).join('')
  );
};

const rows = [
  ['ink / canvas', (c) => [c.ink, c.canvas, 4.5, '正文']],
  ['ink / panel', (c) => [c.ink, c.panel, 4.5, '卡片正文']],
  ['ink2 / panel', (c) => [c.ink2, c.panel, 4.5, '次要文字']],
  ['ink2 / panel2', (c) => [c.ink2, c.panel2, 4.5, '次要文字/浮层']],
  ['ink3 / canvas', (c) => [c.ink3, c.canvas, 4.5, '辅助文字']],
  ['ink3 / panel', (c) => [c.ink3, c.panel, 4.5, '辅助文字']],
  ['ink3 / panel2', (c) => [c.ink3, c.panel2, 4.5, '底栏未选中']],
  ['brand@0.72 / panel', (c) => [alpha(c.brand, c.panel, 0.72), c.panel, 3.0, '图表安静柱']],
  ['ink3@0.72 / panel', (c) => [alpha(c.ink3, c.panel, 0.72), c.panel, 3.0, '折叠备注（过渡态）']],
  ['brandInk / brandTint', (c) => [c.brandInk, c.brandTint, 4.5, '底栏选中字']],
  ['brand / panel', (c) => [c.brand, c.panel, 3.0, '强调/图标']],
  ['expense / panel', (c) => [c.expense, c.panel, 4.5, '支出金额']],
  ['income / panel', (c) => [c.income, c.panel, 4.5, '收入金额']],
  ['line / panel', (c) => [c.line, c.panel, 1.2, '分隔线（可见即可）']],
  ['lineStrong / panel', (c) => [c.lineStrong, c.panel, 1.5, '描边（视觉）']],
  ['panel / canvas', (c) => [c.panel, c.canvas, 1.15, '纸面抬升']],
  ['panel2 / panel', (c) => [c.panel2, c.panel, 1.1, '浮层抬升']],
  ['brandTint / panel', (c) => [c.brandTint, c.panel, 1.1, '选中滑块']],
];

const palettes = readDarkPalettes(source);
const names = Object.keys(palettes);
if (!names.length) throw new Error('Theme.kt 里没读到任何 *Dark PotColors');

let bad = 0;
const table = { before: BEFORE, ...palettes };
for (const [name, palette] of Object.entries(table)) {
  const misses = [];
  for (const [label, fn] of rows) {
    const [fg, bg, min] = fn(palette);
    if (ratio(fg, bg) < min) misses.push(`${label} ${ratio(fg, bg).toFixed(2)}<${min}`);
  }
  bad += misses.length;
  const brief = [
    `底 ${palette.canvas}`,
    `板 ${palette.panel}`,
    `ink3 ${ratio(palette.ink3, palette.panel).toFixed(2)}`,
    `层级 ${ratio(palette.panel, palette.canvas).toFixed(2)}`,
  ].join(' · ');
  console.log(
    `${misses.length ? 'BAD ' : 'OK  '} ${name.padEnd(12)} ${brief}${misses.length ? '\n      -> ' + misses.join('; ') : ''}`,
  );
}
console.log(`\n读了 ${names.length} 套深色纸，不合格组合：${bad}`);
process.exitCode = bad ? 1 : 0;
