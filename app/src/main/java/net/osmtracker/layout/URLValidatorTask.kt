package net.osmtracker.layout

import android.os.AsyncTask
import android.util.Log
import net.osmtracker.util.URLCreator
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

open class URLValidatorTask : AsyncTask<String, Int, Boolean>() {

	public override fun doInBackground(vararg params: String): Boolean {
		val githubUsername = params[0]
		val repoName = params[1]
		val branchName = params[2]
		return customLayoutsRepoValidator(githubUsername, repoName, branchName)
	}

	fun customLayoutsRepoValidator(githubUsername: String, repoName: String, branchName: String): Boolean {
		val serverUrl = URLCreator.createTestURL(githubUsername, repoName, branchName)
		Log.d(TAG, "Resource URL: $serverUrl")
		return try {
			val url = URL(serverUrl)
			HttpsURLConnection.setDefaultSSLSocketFactory(TLSSocketFactory())
			val httpConnection = url.openConnection() as HttpsURLConnection
			if (httpConnection.responseCode == HttpURLConnection.HTTP_OK) {
				Log.i(TAG, "Server returned HTTP ${httpConnection.responseCode} ${httpConnection.responseMessage}")
				true
			} else {
				Log.e(TAG, "The connection could not be established, server return: ${httpConnection.responseCode} ${httpConnection.responseMessage}")
				false
			}
		} catch (e: Exception) {
			Log.e(TAG, "Error. Exception: $e")
			e.printStackTrace()
			false
		}
	}

	companion object {
		const val TAG: String = "URLValidatorTask"
	}
}


