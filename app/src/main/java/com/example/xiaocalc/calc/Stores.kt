package com.example.xiaocalc.calc

import android.content.Context

/** 设置的读写抽象：让 [CalculatorState] 保持纯 Kotlin、可测。 */
interface SettingsStore {
    fun load(): CalcSettings
    fun save(settings: CalcSettings)
}

/** 历史记录读写抽象。方法名刻意避开 `load/save`，以便与 [SettingsStore] 由同一实现承担。 */
interface HistoryStore {
    fun loadEntries(): List<HistoryEntry>
    fun saveEntries(entries: List<HistoryEntry>)
}

/** 首次运行引导状态。 */
interface WelcomeStore {
    fun isWelcomeCompleted(): Boolean
    fun markWelcomeCompleted()
}

/**
 * 三个仓库的合并视图。
 *
 * 合起来是有意的：它们背后是同一份 SharedPreferences 载体，
 * 拆成三个对象只会让构造链更长，而方法名互不冲突。
 */
interface Stores : SettingsStore, HistoryStore, WelcomeStore

/** 内存实现：单元测试与 Compose 预览使用。 */
class InMemorySettingsStore(private var settings: CalcSettings = CalcSettings()) : SettingsStore {
    override fun load(): CalcSettings = settings
    override fun save(settings: CalcSettings) {
        this.settings = settings
    }
}

class InMemoryHistoryStore(private var entries: List<HistoryEntry> = emptyList()) : HistoryStore {
    override fun loadEntries(): List<HistoryEntry> = entries
    override fun saveEntries(entries: List<HistoryEntry>) {
        this.entries = entries
    }
}

class InMemoryWelcomeStore(private var completed: Boolean = false) : WelcomeStore {
    override fun isWelcomeCompleted(): Boolean = completed
    override fun markWelcomeCompleted() {
        completed = true
    }
}

/**
 * SharedPreferences 实现。
 *
 * 设置文件与 key 沿用旧版的 `calculator_settings` / `root` `scientific` `symbols`，
 * 保证老用户升级后三项开关不丢；新增项使用新 key，缺省时回落到合理默认值。
 * 引导状态同样沿用旧版 `getPreferences()` 生成的 `MainActivityPreferences` 文件。
 */
class SharedPreferencesStores(context: Context) : Stores {

    private val appContext = context.applicationContext
    private val settingsPrefs = appContext.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
    private val historyPrefs = appContext.getSharedPreferences(PREFS_HISTORY, Context.MODE_PRIVATE)
    private val welcomePrefs = appContext.getSharedPreferences(PREFS_WELCOME, Context.MODE_PRIVATE)

    // ------------------------------------------------------------------ 设置

    override fun load(): CalcSettings = CalcSettings(
        keepRoot = settingsPrefs.getBoolean(CalcSettings.KEY_ROOT, false),
        scientific = settingsPrefs.getBoolean(CalcSettings.KEY_SCIENTIFIC, false),
        keepSymbols = settingsPrefs.getBoolean(CalcSettings.KEY_SYMBOLS, false),
        angleUnit = if (settingsPrefs.getString(CalcSettings.KEY_ANGLE, ANGLE_DEG) == ANGLE_RAD) {
            AngleUnit.RAD
        } else {
            AngleUnit.DEG
        },
        groupDigits = settingsPrefs.getBoolean(CalcSettings.KEY_GROUP, true),
        haptics = settingsPrefs.getBoolean(CalcSettings.KEY_HAPTICS, true),
    )

    override fun save(settings: CalcSettings) {
        settingsPrefs.edit()
            .putBoolean(CalcSettings.KEY_ROOT, settings.keepRoot)
            .putBoolean(CalcSettings.KEY_SCIENTIFIC, settings.scientific)
            .putBoolean(CalcSettings.KEY_SYMBOLS, settings.keepSymbols)
            .putString(
                CalcSettings.KEY_ANGLE,
                if (settings.angleUnit == AngleUnit.RAD) ANGLE_RAD else ANGLE_DEG,
            )
            .putBoolean(CalcSettings.KEY_GROUP, settings.groupDigits)
            .putBoolean(CalcSettings.KEY_HAPTICS, settings.haptics)
            .apply()
    }

    // ------------------------------------------------------------------ 历史

    override fun loadEntries(): List<HistoryEntry> =
        HistoryCodec.decode(historyPrefs.getString(KEY_HISTORY, null))

    override fun saveEntries(entries: List<HistoryEntry>) {
        historyPrefs.edit().putString(KEY_HISTORY, HistoryCodec.encode(entries)).apply()
    }

    // ------------------------------------------------------------------ 引导

    override fun isWelcomeCompleted(): Boolean = welcomePrefs.getBoolean(KEY_WELCOME, false)

    override fun markWelcomeCompleted() {
        welcomePrefs.edit().putBoolean(KEY_WELCOME, true).apply()
    }

    private companion object {
        const val PREFS_SETTINGS = "calculator_settings"
        const val PREFS_HISTORY = "calculator_history"

        /**
         * 旧版引导标记所在的文件。
         *
         * 旧代码用的是 `Activity.getPreferences(MODE_PRIVATE)`，它等价于
         * `getSharedPreferences(getLocalClassName(), MODE_PRIVATE)`，
         * 而 `getLocalClassName()` 返回的是**去掉包名的类名**——即 `MainActivity`，
         * 因此文件是 `MainActivity.xml`，**不是** `MainActivityPreferences`。
         *
         * 这个文件名是在真机上读取 `/data/data/.../shared_prefs/` 时确认的：
         * 写错会让老用户在升级后被重新弹一次欢迎页。
         */
        const val PREFS_WELCOME = "MainActivity"
        const val KEY_HISTORY = "entries"
        const val KEY_WELCOME = "welcome_completed"
        const val ANGLE_DEG = "deg"
        const val ANGLE_RAD = "rad"
    }
}
