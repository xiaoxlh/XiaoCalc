import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Kotlin 2.0 起，Compose 编译器由独立插件接管（不再用 composeOptions.kotlinCompilerExtensionVersion）
    id("org.jetbrains.kotlin.plugin.compose")
    // 仅用于本地无设备截图（src/test），不参与 APK 打包
    id("app.cash.paparazzi")
}

android {
    namespace = "com.example.xiaocalc"
    // material3 1.4.0（M3 Expressive）的 AAR 元数据要求 compileSdk ≥ 36
    compileSdk = 36

    val keystoreProperties = Properties()
    val keystoreFile = rootProject.file("keystore.properties")
    if (keystoreFile.exists()) {
        keystoreProperties.load(keystoreFile.inputStream())
    }

    signingConfigs {
        create("release") {
            if (keystoreFile.exists()) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.example.xiaocalc"
        minSdk = 26 // Android 8.0，Wear OS 2/3/4 均覆盖
        targetSdk = 34
        versionCode = 6
        versionName = "1.2.0"
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        // AGP 8 起默认关闭，设置页需要读取 BuildConfig.VERSION_NAME
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// Kotlin 2.3 起 `android { kotlinOptions { jvmTarget } }` 已被移除，
// 必须改用顶层 kotlin { compilerOptions } DSL。
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    // Compose 版本统一由 BOM 决定：ui / foundation / runtime 1.11.2，material3 1.4.0
    // 选 2026.05.01 而非最新 BOM，是因为 Paparazzi 2.0.0-alpha05 官方声明的 Compose 版本
    // 恰为 1.11.2；再往上（1.12.x）会硬性要求 AGP 9.1 + compileSdk 37。
    // material3 1.4.0 是 Material 3 Expressive 的起始版本，该 BOM 已包含。
    val composeBom = platform("androidx.compose:compose-bom:2026.05.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    // 仅用核心图标集（约 40 个），不引 material-icons-extended（体积 +30MB）
    implementation("androidx.compose.material:material-icons-core")

    /**
     * Google 官方手表组件库（androidx.wear.compose）。
     *
     * 选 1.6.2 而非 1.7.1：后者的 AAR 元数据硬性要求 `minCompileSdk=37, minAGP=9.1.0`，
     * 而本工程按 Paparazzi 的配对要求固定在 AGP 8.13.2 / compileSdk 36。
     * 1.6.2 的元数据为 `minCompileSdk=35, minAGP=8.6.0`，满足。
     *
     * 用途仅限"官方已经有规范实现"的部件：时间文本、缩放列表、页码指示器、
     * 开关、芯片等。计算器键盘与圆盘布局继续手写——官方库没有对应组件。
     */
    implementation("androidx.wear.compose:compose-material3:1.6.2")
    implementation("androidx.wear.compose:compose-foundation:1.6.2")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
