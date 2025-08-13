package net.osmtracker.util

import android.content.SharedPreferences
import android.content.res.Resources
import net.osmtracker.OSMTracker
import net.osmtracker.R

object ThemeValidator {

	@JvmStatic
	fun getValidTheme(prefs: SharedPreferences, res: Resources): String {
		var theme = prefs.getString(OSMTracker.Preferences.KEY_UI_THEME, OSMTracker.Preferences.VAL_UI_THEME)
		val validThemes = res.getStringArray(R.array.prefs_theme_values).toList()
		if (theme == null || !validThemes.contains(theme)) {
			theme = OSMTracker.Preferences.VAL_UI_THEME
			prefs.edit().putString(OSMTracker.Preferences.KEY_UI_THEME, OSMTracker.Preferences.VAL_UI_THEME).commit()
		}
		return theme
	}
}


