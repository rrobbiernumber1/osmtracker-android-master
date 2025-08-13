package net.osmtracker.util

import android.content.SharedPreferences
import android.content.res.Resources
import net.osmtracker.OSMTracker
import net.osmtracker.R

object ThemeValidator {

	@JvmStatic
	fun getValidTheme(prefs: SharedPreferences, res: Resources): String {
		var theme = prefs.getString(OSMTracker.Preferences.KEY_UI_THEME, OSMTracker.Preferences.VAL_UI_THEME)
		if (theme == null) theme = OSMTracker.Preferences.VAL_UI_THEME
		val themesArray = try { res.getStringArray(R.array.prefs_theme_values) } catch (_: Exception) { null }
		val validThemes = themesArray?.toList() ?: emptyList()
		if (!validThemes.contains(theme)) {
			theme = OSMTracker.Preferences.VAL_UI_THEME
			val editor = prefs.edit()
			editor.putString(OSMTracker.Preferences.KEY_UI_THEME, OSMTracker.Preferences.VAL_UI_THEME)
			editor.commit()
		}
		return theme
	}
}


