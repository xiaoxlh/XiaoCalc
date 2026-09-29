import java.util.Properties
import org.gradle.api.attributes.java.TargetJvmEnvironment

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
    compileSdk = 34

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
        versionCode = 3
        versionName = "1.1.0"
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // Compose 版本统一由 BOM 决定：ui / foundation / material3 均为 1.7.3 / 1.3.0
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
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

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")

    /**
     * Paparazzi / layoutlib 必须拿到 Guava 的 **STANDARD_JVM(-jre) 变体**。
     * Android 工程默认会把 Guava 解析成 `-android` 变体，而该变体缺少
     * `com.google.common.collect.Sets#toImmutableEnumSet`，导致截图测试启动时抛
     * IllegalAccessError（Paparazzi 官方 changelog 里记录的已知问题）。
     *
     * 这条约束只作用于 testImplementation，不影响 APK 依赖解析。
     */
    constraints {
        testImplementation("com.google.guava:guava") {
            attributes {
                attribute(
                    TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
                    objects.named(TargetJvmEnvironment::class.java, TargetJvmEnvironment.STANDARD_JVM),
                )
            }
            because("Paparazzi/layoutlib 需要 Guava 的标准 JVM 变体")
        }
    }
}
