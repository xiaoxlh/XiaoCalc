package com.example.xiaocalc.calc

import android.content.Context

interface SettingsStore {
    fun load(): CalcSettings
    fun save(settings: CalcSettings)
}

interface HistoryStore {
    fun loadEntries(): List<HistoryEntry>
    fun saveEntries(entries: List<HistoryEntry>)
}

interface WelcomeStore {
    fun isWelcomeCompleted(): Boolean
    fun markWelcomeCompleted()
}

interface RateStore {
    fun loadRates(): RateTable?
    fun saveRates(table: RateTable)
}

interface Stores : SettingsStore, HistoryStore, WelcomeStore, RateStore

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

class InMemoryRateStore(private var table: RateTable? = null) : RateStore {
    override fun loadRates(): RateTable? = table
    override fun saveRates(table: RateTable) {
        this.table = table
    }
}

class SharedPreferencesStores(context: Context) : Stores {
    private val appContext = context.applicationContext
    private val settingsPrefs = appContext.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
    private val historyPrefs = appContext.getSharedPreferences(PREFS_HISTORY, Context.MODE_PRIVATE)
    private val welcomePrefs = appContext.getSharedPreferences(PREFS_WELCOME, Context.MODE_PRIVATE)

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
        themeColor = ThemeColor.fromName(settingsPrefs.getString(CalcSettings.KEY_THEME, null)),
        allowNetwork = settingsPrefs.getBoolean(CalcSettings.KEY_NETWORK, false),
        decimals = settingsPrefs
            .getInt(CalcSettings.KEY_DECIMALS, CalcSettings.FULL)
            .coerceIn(0, CalcSettings.FULL),
        blurEnabled = settingsPrefs.getBoolean(CalcSettings.KEY_BLUR, true),
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
            .putString(CalcSettings.KEY_THEME, settings.themeColor.name)
            .putBoolean(CalcSettings.KEY_NETWORK, settings.allowNetwork)
            .putInt(CalcSettings.KEY_DECIMALS, settings.decimals.coerceIn(0, CalcSettings.FULL))
            .putBoolean(CalcSettings.KEY_BLUR, settings.blurEnabled)
            .apply()
    }

    override fun loadEntries(): List<HistoryEntry> =
        HistoryCodec.decode(historyPrefs.getString(KEY_HISTORY, null))

    override fun saveEntries(entries: List<HistoryEntry>) {
        historyPrefs.edit().putString(KEY_HISTORY, HistoryCodec.encode(entries)).apply()
    }

    override fun isWelcomeCompleted(): Boolean = welcomePrefs.getBoolean(KEY_WELCOME, false)

    override fun markWelcomeCompleted() {
        welcomePrefs.edit().putBoolean(KEY_WELCOME, true).apply()
    }

    override fun loadRates(): RateTable? =
        RateCodec.decode(settingsPrefs.getString(KEY_RATES, null))

    override fun saveRates(table: RateTable) {
        settingsPrefs.edit().putString(KEY_RATES, RateCodec.encode(table)).apply()
    }

    private companion object {
        const val PREFS_SETTINGS = "calculator_settings"
        const val PREFS_HISTORY = "calculator_history"
        const val PREFS_WELCOME = "MainActivity"
        const val KEY_HISTORY = "entries"
        const val KEY_RATES = "rates"
        const val KEY_WELCOME = "welcome_completed"
        const val ANGLE_DEG = "deg"
        const val ANGLE_RAD = "rad"
    }
}
