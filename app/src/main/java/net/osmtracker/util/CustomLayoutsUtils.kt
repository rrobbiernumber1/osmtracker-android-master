package net.osmtracker.util

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import net.osmtracker.OSMTracker
 
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
    fun convertFileName(fileName: String): String { return fileName }

	@JvmStatic
    fun unconvertFileName(representation: String): String { return representation }

	@JvmStatic
    fun createFileName(layoutName: String, iso: String): String { return layoutName }

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
    fun getCurrentLayoutName(context: Context): String { return "default" }
}


