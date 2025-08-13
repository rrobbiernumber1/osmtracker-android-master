package net.osmtracker.gpx

import android.content.Context
import android.util.Log
import net.osmtracker.db.DataHelper
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ZipHelper {

	private const val TAG: String = "ZipHelper"

	@JvmStatic
	fun zipCacheFiles(context: Context, trackId: Long, fileGPX: File): File? {
		val name = fileGPX.name
		val zipFile = File(
			context.cacheDir,
			name.substring(0, name.lastIndexOf('.')) + DataHelper.EXTENSION_ZIP
		)

		val traceFilesDirectory = DataHelper.getTrackDirectory(trackId, context)

		return try {
			FileOutputStream(zipFile).use { fos ->
				ZipOutputStream(fos).use { zos ->
					addFileToZip(fileGPX, zos)
					if (!traceFilesDirectory.exists()) return zipFile
					for (multimediaFile in traceFilesDirectory.listFiles() ?: emptyArray()) {
						if (!multimediaFile.isDirectory) {
							if (!multimediaFile.name.endsWith(DataHelper.EXTENSION_ZIP)) {
								addFileToZip(multimediaFile, zos)
							} else {
								Log.d(TAG, "Multimedia file: ${multimediaFile.absolutePath} ignored. ")
							}
						} else {
							Log.d(TAG, "Folder ${multimediaFile.absolutePath} ignored. ")
						}
					}
					Log.d(TAG, "ZIP file created: ${zipFile.absolutePath}")
					zipFile
				}
			}
		} catch (e: IOException) {
			Log.e(TAG, "Error creating ZIP file", e)
			null
		}
	}

	@Throws(IOException::class)
	private fun addFileToZip(file: File, zos: ZipOutputStream) {
		if (!file.exists()) {
			Log.e(TAG, "File does not exist: ${file.absolutePath}")
			return
		}
		FileInputStream(file).use { fis ->
			val zipEntry = ZipEntry(file.name)
			zos.putNextEntry(zipEntry)
			val buffer = ByteArray(1024)
			var length: Int
			while (fis.read(buffer).also { length = it } > 0) {
				zos.write(buffer, 0, length)
			}
			zos.closeEntry()
		}
	}
}


