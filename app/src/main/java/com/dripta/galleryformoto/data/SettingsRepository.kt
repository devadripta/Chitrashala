package com.dripta.galleryformoto.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("gallery_prefs", Context.MODE_PRIVATE)

    private val _gridColumns = MutableStateFlow(
        prefs.getInt(KEY_GRID_COLUMNS, DEFAULT_GRID_COLUMNS)
    )

    private val _themeMode = MutableStateFlow(
        ThemeMode.valueOf(prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
    )

    val gridColumns: StateFlow<Int> = _gridColumns
    val themeMode: StateFlow<ThemeMode> = _themeMode

    fun setGridColumns(count: Int) {
        _gridColumns.value = count
        prefs.edit().putInt(KEY_GRID_COLUMNS, count).apply()
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    companion object {
        const val DEFAULT_GRID_COLUMNS = 3
        private const val KEY_GRID_COLUMNS = "grid_columns"
        private const val KEY_THEME = "theme_mode"
    }
}
