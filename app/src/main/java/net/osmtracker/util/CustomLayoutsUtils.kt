package net.osmtracker.util

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import net.osmtracker.OSMTracker
import net.osmtracker.activity.AvailableLayouts
import net.osmtracker.activity.Preferences
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.io.StringWriter
import java.io.Writer
import java.nio.charset.StandardCharsets

object CustomLayoutsUtils {
	@JvmField
	val LAYOUT_EXTENSION_ISO: String = "_xx.xml"

	@JvmStatic
	fun convertFileName(fileName: String): String {
		var subname = fileName.replace(Preferences.LAYOUT_FILE_EXTENSION, "")
		if (subname.matches(Regex("\\w+_.."))) {
			subname = subname.substring(0, subname.length - (AvailableLayouts.ISO_CHARACTER_LENGTH + 1))
		}
		return subname.replace("_", " ")
	}

	@JvmStatic
	fun unconvertFileName(representation: String): String {
		return representation.replace(" ", "_") + Preferences.LAYOUT_FILE_EXTENSION
	}

	@JvmStatic
	fun createFileName(layoutName: String, iso: String): String {
		var fileName = layoutName.replace(" ", "_")
		fileName += LAYOUT_EXTENSION_ISO.replace("xx", iso)
		return fileName
	}

	@JvmStatic
	@Throws(IOException::class)
	fun getStringFromStream(stream: InputStream?): String {
		if (stream != null) {
			val writer: Writer = StringWriter()
			val buffer = CharArray(2048)
			try {
				val reader: Reader = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8))
				var counter: Int
				while (reader.read(buffer).also { counter = it } != -1) {
					writer.write(buffer, 0, counter)
				}
			} finally {
				stream.close()
			}
			return writer.toString()
		} else {
			throw IOException()
		}
	}

	@JvmStatic
	fun getCurrentLayoutName(context: Context): String {
		var layoutName = OSMTracker.Preferences.VAL_UI_BUTTONS_LAYOUT
		try {
			val sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
			layoutName = sharedPreferences.getString(
				OSMTracker.Preferences.KEY_UI_BUTTONS_LAYOUT,
				OSMTracker.Preferences.VAL_UI_BUTTONS_LAYOUT
			) ?: OSMTracker.Preferences.VAL_UI_BUTTONS_LAYOUT
		}catch (_: Exception) {}
		return layoutName
	}
}


