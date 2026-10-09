package com.kudo.android.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String) { SYSTEM("跟随系统"), LIGHT("浅色"), DARK("深色") }
enum class GlassMode(val label: String) { LIQUID("液态玻璃"), BLUR("背景模糊"), TRANSLUCENT("半透明") }
enum class TopicListStyle(val label: String) { FEED("信息流"), TITLE("传统标题") }
data class DisplaySettings(val theme: ThemeMode = ThemeMode.SYSTEM, val glass: GlassMode = GlassMode.LIQUID, val fontScale: Float = 1f, val topicListStyle: TopicListStyle = TopicListStyle.FEED, val showSignatures: Boolean = false)
data class Draft(val title: String = "", val raw: String = "", val categoryId: Int = 0)

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("kudo_preferences", Context.MODE_PRIVATE)
    private val _display = MutableStateFlow(DisplaySettings(
        ThemeMode.entries.firstOrNull { it.name == prefs.getString("theme", "") } ?: ThemeMode.SYSTEM,
        GlassMode.entries.firstOrNull { it.name == prefs.getString("glass", "") } ?: GlassMode.LIQUID,
        prefs.getFloat("font_scale", 1f).coerceIn(0.85f, 1.3f),
        TopicListStyle.entries.firstOrNull { it.name == prefs.getString("topic_list_style", "") } ?: TopicListStyle.FEED,
        prefs.getBoolean("show_signatures", false),
    ))
    val display = _display.asStateFlow()
    fun update(value: DisplaySettings) {
        _display.value = value
        prefs.edit().putString("theme", value.theme.name).putString("glass", value.glass.name).putFloat("font_scale", value.fontScale).putString("topic_list_style", value.topicListStyle.name).putBoolean("show_signatures", value.showSignatures).apply()
    }
    fun draft(key: String): Draft = Draft(prefs.getString("draft_${key}_title", "").orEmpty(), prefs.getString("draft_${key}_raw", "").orEmpty(), prefs.getInt("draft_${key}_category", 0))
    fun saveDraft(key: String, draft: Draft) { prefs.edit().putString("draft_${key}_title", draft.title).putString("draft_${key}_raw", draft.raw).putInt("draft_${key}_category", draft.categoryId).apply() }
    fun clearDraft(key: String) { prefs.edit().remove("draft_${key}_title").remove("draft_${key}_raw").remove("draft_${key}_category").apply() }
    fun clearAccountData() {
        prefs.edit().apply { prefs.all.keys.filter { it.startsWith("draft_") }.forEach(::remove) }.apply()
    }
}
