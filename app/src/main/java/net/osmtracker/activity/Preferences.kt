package net.osmtracker.activity

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.Editor
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.preference.*
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import net.osmtracker.OSMTracker
import net.osmtracker.R
import java.io.File

class Preferences : PreferenceActivity() {

	companion object {
		const val LAYOUTS_SUBDIR = "layouts"
		const val LAYOUT_FILE_EXTENSION = ".xml"
		const val ICONS_DIR_SUFFIX = "_icons"
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		addPreferencesFromResource(R.xml.preferences)
		val listView: ListView = listView
		listView.fitsSystemWindows = true
		listView.clipToPadding = false
		listView.setPadding(0, 48, 0, 0)
		val prefs = PreferenceManager.getDefaultSharedPreferences(this)
		val buttonLayoutPref: Preference? = findPreference("prefs_ui_buttons_layout")
		buttonLayoutPref?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
			val intent = Intent(this, ButtonsPresets::class.java)
			startActivity(intent)
			true
		}
		val storageDirPref = findPreference(OSMTracker.Preferences.KEY_STORAGE_DIR) as EditTextPreference
		storageDirPref.summary = prefs.getString(OSMTracker.Preferences.KEY_STORAGE_DIR, OSMTracker.Preferences.VAL_STORAGE_DIR)
		storageDirPref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
			var nv = newValue as String
			if (!nv.startsWith(File.separator)) nv = File.separator + nv
			preference.summary = nv
			true
		}
		var pref: Preference = findPreference(OSMTracker.Preferences.KEY_VOICEREC_DURATION)
		pref.summary = prefs.getString(OSMTracker.Preferences.KEY_VOICEREC_DURATION, OSMTracker.Preferences.VAL_VOICEREC_DURATION) + " " + resources.getString(R.string.prefs_voicerec_duration_seconds)
		pref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
			preference.summary = newValue.toString() + " " + resources.getString(R.string.prefs_voicerec_duration_seconds)
			true
		}
		pref = findPreference(OSMTracker.Preferences.KEY_USE_BAROMETER)
		pref.summary = resources.getString(R.string.prefs_use_barometer_summary)
		pref = findPreference(OSMTracker.Preferences.KEY_GPS_LOGGING_INTERVAL)
		pref.summary = prefs.getString(OSMTracker.Preferences.KEY_GPS_LOGGING_INTERVAL, OSMTracker.Preferences.VAL_GPS_LOGGING_INTERVAL) + " " + resources.getString(R.string.prefs_gps_logging_interval_seconds) + ". " + resources.getString(R.string.prefs_gps_logging_interval_summary)
		pref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
			preference.summary = newValue.toString() + " " + resources.getString(R.string.prefs_gps_logging_interval_seconds) + ". " + resources.getString(R.string.prefs_gps_logging_interval_summary)
			true
		}
		pref = findPreference(OSMTracker.Preferences.KEY_GPS_LOGGING_MIN_DISTANCE)
		pref.summary = prefs.getString(OSMTracker.Preferences.KEY_GPS_LOGGING_MIN_DISTANCE, OSMTracker.Preferences.VAL_GPS_LOGGING_MIN_DISTANCE) + " " + resources.getString(R.string.prefs_gps_logging_min_distance_meters) + ". " + resources.getString(R.string.prefs_gps_logging_min_distance_summary)
		pref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
			preference.summary = newValue.toString() + " " + resources.getString(R.string.prefs_gps_logging_min_distance_meters) + ". " + resources.getString(R.string.prefs_gps_logging_min_distance_summary)
			true
		}
		val et = (pref as EditTextPreference).editText
		val etp = pref
		et.addTextChangedListener(object : TextWatcher {
			override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
			override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
				if (s.isNotEmpty()) {
					try {
						val btOk: Button = (etp.dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE)
						btOk.isEnabled = s.isNotEmpty()
					} catch (_: Exception) {}
				}
			}
			override fun afterTextChanged(s: Editable) {}
		})
		pref = findPreference(OSMTracker.Preferences.KEY_GPS_OSSETTINGS)
		pref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
			startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
			true
		}
		var orientationPref: Preference = findPreference(OSMTracker.Preferences.KEY_UI_ORIENTATION)
		val orientationListPreference = orientationPref as ListPreference
		val displayValueKey = prefs.getString(OSMTracker.Preferences.KEY_UI_ORIENTATION, OSMTracker.Preferences.VAL_UI_ORIENTATION)
		val displayValueIndex = orientationListPreference.findIndexOfValue(displayValueKey)
		val displayValue = orientationListPreference.entries[displayValueIndex].toString()
		orientationListPreference.summary = displayValue + ".\n" + resources.getString(R.string.prefs_ui_orientation_summary)
		orientationPref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
			val lp = preference as ListPreference
			val newValueIndex = lp.findIndexOfValue(newValue as String)
			val newPreferenceDisplayValue = lp.entries[newValueIndex].toString()
			preference.summary = newPreferenceDisplayValue + ".\n" + resources.getString(R.string.prefs_ui_orientation_summary)
			true
		}
		val clearOSMPref: Preference = findPreference(OSMTracker.Preferences.KEY_OSM_OAUTH_CLEAR_DATA)
		if (prefs.contains(OSMTracker.Preferences.KEY_OSM_OAUTH2_ACCESSTOKEN)) clearOSMPref.isEnabled = true else clearOSMPref.isEnabled = false
		clearOSMPref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, _ ->
			val editor: Editor = prefs.edit()
			editor.remove(OSMTracker.Preferences.KEY_OSM_OAUTH2_ACCESSTOKEN)
			editor.commit()
			preference.isEnabled = false
			false
		}
	}
}


