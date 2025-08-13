package net.osmtracker.layout

import android.os.AsyncTask
import android.util.Log
import net.osmtracker.util.CustomLayoutsUtils
import java.net.URL
import javax.net.ssl.HttpsURLConnection

open class GetStringResponseTask : AsyncTask<String, Int, String?>() {

	override fun doInBackground(vararg params: String): String? {
		return try {
			val url = URL(params[0])
			HttpsURLConnection.setDefaultSSLSocketFactory(TLSSocketFactory())
			val connection = url.openConnection() as HttpsURLConnection
			CustomLayoutsUtils.getStringFromStream(connection.inputStream)
		} catch (e: Exception) {
			Log.e(TAG, "Error. Exception: $e")
			e.printStackTrace()
			null
		}
	}

	companion object {
		private const val TAG = "GetStringResponseTask"
	}
}


