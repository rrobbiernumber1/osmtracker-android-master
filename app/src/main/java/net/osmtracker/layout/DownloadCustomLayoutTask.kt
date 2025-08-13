package net.osmtracker.layout

import android.content.Context
import android.content.SharedPreferences
import android.os.AsyncTask
import android.os.Environment
import android.preference.PreferenceManager
import android.util.Log
import net.osmtracker.OSMTracker
import net.osmtracker.R
import net.osmtracker.activity.Preferences
import net.osmtracker.util.CustomLayoutsUtils
import net.osmtracker.util.URLCreator
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

open class DownloadCustomLayoutTask(protected val context: Context) : AsyncTask<String, Int, Boolean>() {

	private val TAG = "Download Custom Layout"

	override fun doInBackground(vararg layoutData: String): Boolean {
		val layoutName = layoutData[0]
		val iso = layoutData[1]
		return downloadLayout(layoutName, iso)
	}

	fun downloadLayout(layoutName: String, iso: String): Boolean {
		val layoutFolderName = layoutName.replace(" ", "_")
		val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
		val storageDir = File.separator + OSMTracker.Preferences.VAL_STORAGE_DIR
		Log.d(TAG, "storage directory: $storageDir")

		val layoutURL = URLCreator.createLayoutFileURL(context, layoutFolderName, iso)
		val layoutPath = context.getExternalFilesDir(null).toString() + storageDir + File.separator +
				Preferences.LAYOUTS_SUBDIR + File.separator

		val iconsPath = context.getExternalFilesDir(null).toString() + storageDir + File.separator +
				Preferences.LAYOUTS_SUBDIR + File.separator + layoutFolderName + "_icons" +
				File.separator

		var status = false
		try {
			createDir(layoutPath)
			downloadFile(layoutURL, layoutPath + File.separator +
					CustomLayoutsUtils.createFileName(layoutName, iso))
			status = true

			val iconsInfo = getIconsHash(layoutName)
			if (iconsInfo != null) {
				createDir(iconsPath)
				val keys = iconsInfo.keys
				for (key in keys) {
					val currentValue = iconsInfo[key]
					if (currentValue != null) {
						downloadFile(currentValue, iconsPath + File.separator + key)
					}
				}
			}
		} catch (throwable: Throwable) {
			Log.e(TAG, throwable.toString())
			status = false
		}
		return status
	}

	@Throws(IOException::class)
	private fun createDir(dirPath: String) {
		if (Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED) {
			throw IOException(context.resources.getString(R.string.error_externalstorage_not_writable))
		}
		val directory = File(dirPath)
		if (!directory.exists()) {
			val ok = directory.mkdirs()
			if (!ok) throw IOException(context.resources.getString(R.string.error_externalstorage_not_writable))
		}
		Log.d(TAG, "Directory Created: $directory")
	}

	@Suppress("SameParameterValue")
	@Throws(Throwable::class)
	private fun downloadFile(downloadUrl: String, outputFile: String) {
		val url = URL(downloadUrl)
		val connection = url.openConnection() as HttpURLConnection
		val input: InputStream? = connection.inputStream
		if (input != null) {
			val fos = FileOutputStream(outputFile)
			val buffer = ByteArray(2048)
			var len: Int
			while (input.read(buffer).also { len = it } != -1) {
				fos.write(buffer, 0, len)
			}
			fos.close()
			input.close()
		} else {
			throw IOException("No Contents")
		}
	}

	private fun getIconsHash(layoutName: String): HashMap<String, String>? {
		val iconsHash = HashMap<String, String>()
		val layoutFolderName = layoutName.replace(" ", "_")
		val link = URLCreator.createIconsDirUrl(context, layoutFolderName)
		println("Download icons hash from: $link")
		return try {
			val url = URL(link)
			val httpConnection = url.openConnection() as HttpURLConnection
			val stream = httpConnection.inputStream
			val response = CustomLayoutsUtils.getStringFromStream(stream)
			val jsonArray = JSONArray(response)
			for (i in 0 until jsonArray.length()) {
				val obj = jsonArray.getJSONObject(i)
				iconsHash[obj.getString("name")] = obj.getString("download_url")
			}
			iconsHash
		} catch (e: Exception) {
			Log.e(TAG, e.toString())
			null
		}
	}
}


