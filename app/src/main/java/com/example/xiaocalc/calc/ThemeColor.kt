package com.example.xiaocalc.calc

/**
 * 强调色主题。
 *
 * ## 它控制什么
 *
 * **整块配色**。品牌色就是强调色，页面上不再有一个"不跟着主题走"的结构色——
 * 早期版本让导航与选中态固定用蓝，结果是换了主题之后总有几处还是蓝的，
 * 用户读到的是"主题没生效"，而不是"这里在表达结构"。
 *
 * 层次改用**同一色的深浅**表达：
 * - 主要（当前页、选中态、`=`、结果强调）用 [accent]；
 * - 次要（容器、运算符键底、卡片）用更暗的 [accentContainer]；
 * - 文字压在容器上时用 [onAccentContainer]。
 *
 * 默认 [RED] 与旧版配色**逐值相同**，升级后外观零变化。
 *
 * ## 为什么用 Long 而不是 Compose 的 Color
 *
 * 这个枚举是**设置项**，要能被 `CalcSettings` 保存与比较，
 * 而 `CalcSettings` 位于无 Android 依赖的 calc 层。
 * 展示层负责把 ARGB 转成 `Color`（见 design/Color.kt）。
 *
 * @param accent        强调填充色（`=` 键底色、运算符键容器）
 * @param onAccent      强调填充上的文字色
 * @param accentContainer 次要强调容器（运算符键底色等）
 * @param onAccentContainer 次要强调容器上的文字色
 */
enum class ThemeColor(
    val label: String,
    val accent: Long,
    val onAccent: Long,
    val accentContainer: Long,
    val onAccentContainer: Long,
) {
    /** 默认：与旧版完全一致 */
    RED("红", 0xFFFF5454, 0xFFFFFFFF, 0xFF6B1D1D, 0xFFFFB4B4),
    ORANGE("橙", 0xFFFF9E45, 0xFF3A1B00, 0xFF6B3200, 0xFFFFD8B0),
    AMBER("琥珀", 0xFFFFC53D, 0xFF3A2A00, 0xFF6B4A00, 0xFFFFE9A8),
    GREEN("绿", 0xFF4ADE80, 0xFF04331A, 0xFF12532F, 0xFFA7F3C8),
    CYAN("青", 0xFF38D9E8, 0xFF05343B, 0xFF0C4A52, 0xFFA5EEF7),
    BLUE("蓝", 0xFF5AA9FF, 0xFF04203F, 0xFF123A66, 0xFFBBD9FF),
    PURPLE("紫", 0xFFA78BFA, 0xFF1E0F45, 0xFF35226B, 0xFFDDD0FF),
    PINK("粉", 0xFFF472B6, 0xFF3F0A22, 0xFF5C1B3A, 0xFFFBCFE8),
    ;

    /** 下一个主题（用于"点击循环切换"这种轻量交互） */
    fun next(): ThemeColor = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromName(name: String?): ThemeColor =
            entries.firstOrNull { it.name == name } ?: RED
    }
}
