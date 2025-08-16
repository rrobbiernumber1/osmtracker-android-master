package net.osmtracker.gpx

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.preference.PreferenceManager
import android.util.Log
import net.osmtracker.OSMTracker
import net.osmtracker.R
import net.osmtracker.db.DataHelper
import net.osmtracker.db.model.Track

import net.osmtracker.util.FileSystemUtils.getUniqueChildNameFor
import java.io.File
import java.util.Date

open class ExportToStorageTask(context: Context, private val dataHelper: DataHelper, vararg trackId: Long) : ExportTrackTask(context, *trackId) {

	private val ERROR_MESSAGE: String = context.resources.getString(R.string.error_create_track_dir)
	private var sharedPreferences: SharedPreferences? = null

	constructor(context: Context, vararg trackId: Long) : this(context, DataHelper(context), *trackId)

	override fun getExportDirectory(startDate: Date): File {
		val trackName = getSanitizedTrackNameByStartDate(startDate)
		val shouldCreateDirectoryPerTrack = shouldCreateDirectoryPerTrack()
		var finalExportDirectory = baseExportDirectory
		Log.d(TAG, "absolute dir: ${finalExportDirectory.absolutePath}")
		if (shouldCreateDirectoryPerTrack && (trackName?.isNotEmpty() == true)) {
			val uniqueFolderName = getUniqueChildNameFor(finalExportDirectory, trackName, "")
			finalExportDirectory = File(finalExportDirectory, uniqueFolderName)
			finalExportDirectory.mkdirs()
		}
		if (!finalExportDirectory.exists()) throw RuntimeException(ERROR_MESSAGE)
		return finalExportDirectory
	}

	fun getSanitizedTrackNameByStartDate(startDate: Date): String? {
		val track: Track? = dataHelper.getTrackByStartDate(startDate)
		if (track == null) return ""
		var trackName: String? = track.getName()
		if (trackName != null && trackName.isNotEmpty()) trackName = trackName.replace("/", "_").trim { it <= ' ' }
		return trackName
	}

	fun shouldCreateDirectoryPerTrack(): Boolean {
		ensurePrefs()
		return sharedPreferences!!.getBoolean(
			OSMTracker.Preferences.KEY_OUTPUT_DIR_PER_TRACK,
			OSMTracker.Preferences.VAL_OUTPUT_GPX_OUTPUT_DIR_PER_TRACK
		)
	}

	private fun isExternalStorageWritable(): Boolean = Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED


	val baseExportDirectory: File
		get() {
			if (!isExternalStorageWritable()) {
				throw RuntimeException(context.resources.getString(R.string.error_externalstorage_not_writable))
			}
			ensurePrefs()
			val exportDirectoryNameInPreferences = sharedPreferences!!.getString(
				OSMTracker.Preferences.KEY_STORAGE_DIR,
				OSMTracker.Preferences.VAL_STORAGE_DIR
			) ?: OSMTracker.Preferences.VAL_STORAGE_DIR
			Log.d(TAG, "exportDirectoryNameInPreferences: $exportDirectoryNameInPreferences")
			val baseExportDirectory = File(
				Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
				exportDirectoryNameInPreferences
			)
			if (!baseExportDirectory.exists()) {
				val ok = baseExportDirectory.mkdirs()
				if (!ok) {
					throw RuntimeException(context.resources.getString(R.string.error_externalstorage_not_writable))
				}
			}
			Log.d(TAG, "BaseExportDirectory: ${baseExportDirectory.absolutePath}")
			return baseExportDirectory
		}

	private fun ensurePrefs() {
		if (sharedPreferences == null) {
			sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
		}
	}

	override fun exportMediaFiles(): Boolean = false

	override fun updateExportDate(): Boolean = true

	companion object { private const val TAG = "ExportToStorageTask" }
}


