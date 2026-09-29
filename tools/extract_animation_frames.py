#!/usr/bin/env python3
"""把 Paparazzi 录制的动效 APNG 抽帧拼成一张胶片图，便于肉眼核对中间帧。

用法:
    python tools/extract_animation_frames.py <apng> [输出 png] [列数]

除了出图，脚本还会打印"总帧数 / 互不相同的帧数"——
如果所有帧都一样，说明动画根本没被推进（Paparazzi 版本不支持推进 Compose 帧时钟），
这时胶片图没有意义，应当改回用静帧验证。
"""
from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image, ImageSequence

DEFAULT_COLUMNS = 5
MAX_TILES = 20
LABEL_HEIGHT = 22
SCALE = 0.5


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 2

    source = Path(sys.argv[1])
    if not source.exists():
        print(f"[ERROR] 找不到文件：{source}", file=sys.stderr)
        return 1

    columns = int(sys.argv[3]) if len(sys.argv) > 3 else DEFAULT_COLUMNS
    output = Path(sys.argv[2]) if len(sys.argv) > 2 else source.with_name(
        source.stem + "_filmstrip.png"
    )

    with Image.open(source) as image:
        frames = [frame.convert("RGB").copy() for frame in ImageSequence.Iterator(image)]
        durations = []
        for frame in ImageSequence.Iterator(Image.open(source)):
            durations.append(frame.info.get("duration", 0))

    if not frames:
        print("[ERROR] APNG 中没有帧", file=sys.stderr)
        return 1

    # 去重统计：这是"动画到底有没有跑"的硬证据
    unique = len({frame.tobytes() for frame in frames})
    print(f"总帧数 {len(frames)}，互不相同的帧 {unique}")
    if unique <= 1:
        print("[WARN] 所有帧完全相同——动画未被推进，胶片图无法反映动效", file=sys.stderr)

    # 均匀抽样，避免拼图过长
    step = max(1, len(frames) // MAX_TILES)
    picked = list(range(0, len(frames), step))[:MAX_TILES]

    tile_w = max(1, int(frames[0].width * SCALE))
    tile_h = max(1, int(frames[0].height * SCALE))
    rows = (len(picked) + columns - 1) // columns

    sheet = Image.new(
        "RGB",
        (columns * tile_w, rows * (tile_h + LABEL_HEIGHT)),
        (24, 24, 24),
    )
    for index, frame_index in enumerate(picked):
        col = index % columns
        row = index // columns
        tile = frames[frame_index].resize((tile_w, tile_h), Image.LANCZOS)
        x = col * tile_w
        y = row * (tile_h + LABEL_HEIGHT)
        sheet.paste(tile, (x, y))
        elapsed = sum(durations[:frame_index])
        _draw_label(sheet, x, y + tile_h, f"#{frame_index}  t={elapsed}ms", tile_w)

    sheet.save(output)
    print(f"胶片图已写入 {output}（{len(picked)} 帧，{columns} 列）")
    return 0


def _draw_label(sheet: Image.Image, x: int, y: int, text: str, tile_w: int) -> None:
    """用 PIL 默认位图字体画标签（避免依赖 truetype 字体文件）"""
    from PIL import ImageDraw

    draw = ImageDraw.Draw(sheet)
    draw.rectangle([x, y, x + tile_w - 1, y + LABEL_HEIGHT], fill=(40, 40, 40))
    draw.text((x + 4, y + 5), text, fill=(220, 220, 220))


if __name__ == "__main__":
    raise SystemExit(main())
