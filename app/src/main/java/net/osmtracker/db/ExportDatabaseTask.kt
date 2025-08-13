package net.osmtracker.db

import android.os.AsyncTask
import android.os.Bundle
import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPOutputStream

class ExportDatabaseTask(private val context: Context, private val targetFolder: File) : AsyncTask<File, Float, String>() {

	companion object {
		private const val BUF_SIZE = 16 * 1024
		private const val DB_FILE_EXT = ".sqlitedb.gz"
	}

	override fun doInBackground(vararg files: File): String {
		if (files.size > 1) throw IllegalArgumentException("More than 1 file is not supported")
		val targetFile = File(targetFolder, DatabaseHelper.DB_NAME + DB_FILE_EXT)
		targetFile.parentFile.mkdirs()
		val fileSize = files[0].length()
		var isStream: InputStream? = null
		var osStream: OutputStream? = null
		return try {
			isStream = FileInputStream(files[0])
			osStream = GZIPOutputStream(FileOutputStream(targetFile))
			val buffer = ByteArray(BUF_SIZE)
			var copied = 0L
			var count: Int
			while (isStream.read(buffer).also { count = it } != -1) {
				osStream.write(buffer, 0, count)
				copied += count
				publishProgress((fileSize.toFloat() / copied))
			}
			targetFile.absolutePath
		} catch (e: IOException) {
			e.localizedMessage
		} finally {
			try { isStream?.close() } catch (_: IOException) {}
			try { osStream?.close() } catch (_: IOException) {}
		}
	}

    override fun onProgressUpdate(vararg values: Float?) {}

	override fun onPostExecute(result: String) {
        // About removed: previously showed result dialog; now no-op
	}
}


