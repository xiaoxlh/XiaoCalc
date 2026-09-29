#!/usr/bin/env python3
"""精简 MiSans 字体：为 XiaoCalc 生成 4 个静态字重子集。

用法:
    python tools/subset_font.py                 # 生成 + 自动校验
    python tools/subset_font.py --check         # 只校验现有产物，不重新生成

做法:
    1. 扫描 app/src/main/java 下所有 .kt 源码中的字符串/字符字面量，得到"UI 实际会渲染的字符"；
    2. 并入计算器专用字符集（数字、运算符、上标指数、⌫ 等）与 ASCII；
    3. 用 fontTools.subset 按该字符集裁掉其余字形（4 个字重合计缩减约 99%）；
    4. 回读产物 cmap，逐字校验覆盖率——缺字会在这里被挡下，而不是等上线后显示方块。

依赖: fonttools
"""
from __future__ import annotations

import argparse
import re
import string
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC_DIR = Path(r"D:\Users\xiaox\Downloads\MiSans\MiSans\ttf")
OUT_DIR = ROOT / "app" / "src" / "main" / "java"

# 源字重文件名 -> 输出资源名（Android 资源名必须小写）
WEIGHTS = {
    "MiSans-Regular.ttf": "misans_regular.ttf",
    "MiSans-Medium.ttf": "misans_medium.ttf",
    "MiSans-Bold.ttf": "misans_bold.ttf",
    "MiSans-Heavy.ttf": "misans_heavy.ttf",
}

# 上标指数：结果显示科学计数法时使用（1.23457×10¹²）
SUPERSCRIPTS = "⁰¹²³⁴⁵⁶⁷⁸⁹⁻⁺ⁿ"

# 计算器字形与界面符号（无法只靠源码正则穷尽，显式列出）
#
# 注意：这里刻意**不含** ⌫ (U+232B)——MiSans 没有该字形，旧版本把它当文字渲染，
# 实际依赖系统字体回退；现在退格键改用矢量路径绘制（见 ui/components/Icons.kt）。
CALC_GLYPHS = "0123456789.+-×÷^%()√!=,·°πe"

# 界面里出现的 CJK 标点与常用符号
PUNCTUATION = "：（）！×÷，。·©≥"

# 源码中提取不到、但运行期一定会渲染的文案片段（保底）
FALLBACK_WORDS = "XiaoCalc小计算 Start Calculating on Your Wrist 度 弧度"

LITERAL_PATTERN = re.compile(r'"((?:[^"\\]|\\.)*)"|\'((?:[^\'\\]|\\.)*)\'')


def collect_chars(sources: list[Path]) -> set[str]:
    chars: set[str] = set()
    for path in sources:
        text = path.read_text(encoding="utf-8")
        for match in LITERAL_PATTERN.finditer(text):
            raw = match.group(1) if match.group(1) is not None else match.group(2)
            if raw is None:
                continue
            raw = (
                raw.replace("\\n", " ")
                .replace("\\t", " ")
                .replace('\\"', '"')
                .replace("\\'", "'")
                .replace("\\\\", "\\")
            )
            chars.update(raw)
    return chars


def required_chars(sources: list[Path]) -> str:
    chars = collect_chars(sources)
    chars.update(CALC_GLYPHS)
    chars.update(SUPERSCRIPTS)
    chars.update(PUNCTUATION)
    chars.update(FALLBACK_WORDS)
    # 保证任何运行期数字/结果都能渲染
    chars.update(string.ascii_letters + string.digits + string.punctuation + " ")
    # 去掉控制字符（pyftsubset 会拒绝）
    chars = {c for c in chars if ord(c) >= 0x20 and c != "\u007f"}
    return "".join(sorted(chars))


def subset(src: Path, out: Path, text: str) -> None:
    cmd = [
        sys.executable, "-B", "-m", "fontTools.subset", str(src),
        f"--text={text}",
        "--layout-features=*",
        "--glyph-names",
        "--symbol-cmap",
        "--legacy-cmap",
        "--notdef-glyph",
        "--notdef-outline",
        "--recommended-glyphs",
        "--name-IDs=0,1,2,3,4,6",
        f"--output-file={out}",
    ]
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        print(result.stdout)
        print(result.stderr, file=sys.stderr)
        raise SystemExit(result.returncode)


def verify(font_path: Path, text: str) -> list[str]:
    from fontTools.ttLib import TTFont

    with TTFont(font_path, lazy=True) as font:
        cmap: set[int] = set()
        for table in font["cmap"].tables:
            cmap.update(table.cmap.keys())
    return sorted({c for c in text if ord(c) not in cmap})


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--src-dir", default=str(SRC_DIR))
    parser.add_argument("--out-dir", default=str(OUT_DIR / ".." / "res" / "font"))
    parser.add_argument("--check", action="store_true", help="只校验现有产物")
    args = parser.parse_args()

    src_dir = Path(args.src_dir)
    out_dir = Path(args.out_dir).resolve()

    kotlin_files = sorted((OUT_DIR).rglob("*.kt"))
    if not kotlin_files:
        print(f"[ERROR] 未找到任何 Kotlin 源码于 {OUT_DIR}", file=sys.stderr)
        return 1
    text = required_chars(kotlin_files)
    print(f"扫描 {len(kotlin_files)} 个 Kotlin 文件，需保留 {len(text)} 个字符")

    if not args.check:
        missing = [name for name in WEIGHTS if not (src_dir / name).exists()]
        if missing:
            print(f"[ERROR] 未找到源字体：{', '.join(str(src_dir / m) for m in missing)}", file=sys.stderr)
            return 1
        out_dir.mkdir(parents=True, exist_ok=True)
        for src_name, out_name in WEIGHTS.items():
            src = src_dir / src_name
            out = out_dir / out_name
            before = src.stat().st_size
            subset(src, out, text)
            after = out.stat().st_size
            print(
                f"{out_name}: {before / 1024:.1f} KB -> {after / 1024:.1f} KB "
                f"(缩减 {(1 - after / before) * 100:.1f}%)"
            )

    failures = 0
    for out_name in WEIGHTS.values():
        out = out_dir / out_name
        if not out.exists():
            print(f"[MISS] {out_name} 不存在", file=sys.stderr)
            failures += 1
            continue
        missing = verify(out, text)
        if missing:
            failures += 1
            preview = "".join(missing[:40])
            print(f"[FAIL] {out_name} 缺失 {len(missing)} 个字形：{preview}", file=sys.stderr)
        else:
            print(f"[ OK ] {out_name} 字形覆盖完整（{out.stat().st_size / 1024:.1f} KB）")

    if failures:
        print("字体校验未通过", file=sys.stderr)
        return 1
    print("字体子集生成并校验完成")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
