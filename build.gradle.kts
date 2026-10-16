plugins {
    // v1.2.0 地基升级：从 2024-09 的整套工具链（AGP 8.2.2 / Kotlin 2.0.21 / Compose BOM 2024.09.03
    // / compileSdk 34）迁到当前稳定版。这是启用 Material 3 Expressive 与官方手表组件库的前置条件——
    // Expressive 只存在于 material3 1.4.0，而 1.4.0 要求 compileSdk 36。
    //
    // 版本选择依据（均为读构件元数据/POM 实测，非猜测）：
    //   AGP      8.13.2        —— 8.x 线最新稳定版
    //   Kotlin   2.3.21        —— 与 Paparazzi 2.0.0-alpha04 的基线（Kotlin 2.3.0）同小版本
    //   Gradle   8.14.3        —— AGP 8.13 要求 Gradle ≥ 8.13
    //   Paparazzi 2.0.0-alpha05 —— 依赖 sdk-common 31.13.2，即 AGP 8.13.2 世代，与上面 AGP 精确配对；
    //                              它同时修掉了 alpha04 在 Gradle 8.x 下的
    //                              NoClassDefFoundError: org/gradle/reporting/HtmlWriterTools。
    //                              （alpha05.1 起 sdk-common 升为 32.0.0 = AGP 9.0，故不能用）
    //   需要 JDK 21：Paparazzi 自 2.0.0-alpha04 起要求 Java 21+ 构建环境
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
    // 无设备截图：在 JVM 上用 layoutlib 真实渲染 Compose，产出 PNG 供圆屏复核
    id("app.cash.paparazzi") version "2.0.0-alpha05" apply false
}
