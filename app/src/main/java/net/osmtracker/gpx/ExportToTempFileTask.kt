package net.osmtracker.gpx

import android.content.Context
import android.preference.PreferenceManager
import android.util.Log
import net.osmtracker.OSMTracker
import net.osmtracker.db.DataHelper

import java.io.File
import java.util.Date

abstract class ExportToTempFileTask(context: Context, trackId: Long) : ExportTrackTask(context, trackId) {

	private val tmpFile: File
	private var filename: String? = null

	init {
		val desiredOutputFormat = PreferenceManager.getDefaultSharedPreferences(context).getString(
			OSMTracker.Preferences.KEY_OUTPUT_FILENAME, OSMTracker.Preferences.VAL_OUTPUT_FILENAME
		)
		try {
			val track = DataHelper(context).getTrackById(trackId)
			val trackName = track.getName()
			val startDate = track.getTrackDate()
			val formattedTrackStartDate = DataHelper.FILENAME_FORMATTER.format(Date(startDate))
			val tmpFilename = formatGpxFilename(desiredOutputFormat!!, trackName, formattedTrackStartDate)
			tmpFile = File(context.cacheDir, tmpFilename + DataHelper.EXTENSION_GPX)
			Log.d(TAG, "Temporary file: ${tmpFile.absolutePath}")
		} catch (ioe: Exception) {
			Log.e(TAG, "Could not create temporary file", ioe)
			throw IllegalStateException("Could not create temporary file", ioe)
		}
	}


	override fun getExportDirectory(startDate: Date): File = tmpFile.parentFile ?: tmpFile.parentFile

	public override fun buildGPXFilename(c: android.database.Cursor, parentDirectory: File): String {
		filename = super.buildGPXFilename(c, parentDirectory)
		return tmpFile.name
	}

	override fun exportMediaFiles(): Boolean = false

	override fun updateExportDate(): Boolean = false

	fun getTmpFile(): File = tmpFile

	fun getFilename(): String? = filename

	public override fun onPostExecute(success: Boolean) {
		super.onPostExecute(success)
		executionCompleted()
	}

	protected abstract fun executionCompleted()

	companion object { private const val TAG = "ExportToTempFileTask" }
}


