plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    // 无设备截图：在 JVM 上用 layoutlib 真实渲染 Compose，产出 PNG 供圆屏复核
    id("app.cash.paparazzi") version "1.3.3" apply false
}
