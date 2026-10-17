package com.example.xiaocalc.calc

enum class ThemeColor(
    val label: String,
    val accent: Long,
    val onAccent: Long,
    val accentContainer: Long,
    val onAccentContainer: Long,
) {
    RED("红", 0xFFFF5454, 0xFFFFFFFF, 0xFF6B1D1D, 0xFFFFB4B4),
    ORANGE("橙", 0xFFFF9E45, 0xFF3A1B00, 0xFF6B3200, 0xFFFFD8B0),
    AMBER("琥珀", 0xFFFFC53D, 0xFF3A2A00, 0xFF6B4A00, 0xFFFFE9A8),
    GREEN("绿", 0xFF4ADE80, 0xFF04331A, 0xFF12532F, 0xFFA7F3C8),
    CYAN("青", 0xFF38D9E8, 0xFF05343B, 0xFF0C4A52, 0xFFA5EEF7),
    BLUE("蓝", 0xFF5AA9FF, 0xFF04203F, 0xFF123A66, 0xFFBBD9FF),
    PURPLE("紫", 0xFFA78BFA, 0xFF1E0F45, 0xFF35226B, 0xFFDDD0FF),
    PINK("粉", 0xFFF472B6, 0xFF3F0A22, 0xFF5C1B3A, 0xFFFBCFE8),
    ;

    fun next(): ThemeColor = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromName(name: String?): ThemeColor =
            entries.firstOrNull { it.name == name } ?: RED
    }
}
