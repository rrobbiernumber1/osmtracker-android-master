package net.osmtracker.osm

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.DialogInterface
import android.os.AsyncTask
import android.preference.PreferenceManager
import android.util.Log
import net.osmtracker.OSMTracker
import net.osmtracker.R
import net.osmtracker.db.DataHelper
import net.osmtracker.db.model.Track
import net.osmtracker.util.DialogUtils
import de.westnordost.osmapi.OsmConnection
import de.westnordost.osmapi.common.errors.OsmAuthorizationException
import de.westnordost.osmapi.common.errors.OsmBadUserInputException
import de.westnordost.osmapi.traces.GpsTraceDetails
import de.westnordost.osmapi.traces.GpsTracesApi
import java.io.File
import java.io.FileInputStream
import java.io.IOException

/**
 * Uploads a GPX file to OpenStreetMap
 */
class UploadToOpenStreetMapTask(
	private val activity: Activity,
	private val accessToken: String,
	private val trackId: Long,
	private val gpxFile: File,
	private val filename: String,
	private val description: String?,
	private val tags: String?,
	private val visibility: Track.OSMVisibility?
) : AsyncTask<Void, Void, Void>() {

	private var dialog: ProgressDialog? = null

	private var errorMsg: String? = null

	private var resultCode = -1
	private val authorizationErrorResultCode = -2
	private val anotherErrorResultCode = -3
	private val okResultCode = 1

	private val safeDescription = description ?: "test"
	private val safeTags = tags ?: "test"
	private val safeVisibility = visibility ?: Track.OSMVisibility.Private

	override fun onPreExecute() {
		try {
			// Display progress dialog
			dialog = ProgressDialog(activity)
			dialog!!.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
			dialog!!.isIndeterminate = true
			dialog!!.setTitle(
				activity.resources.getString(R.string.osm_upload_sending)
					.replace("{0}", trackId.toString())
			)
			dialog!!.setCancelable(false)
			dialog!!.show()
		} catch (e: Exception) {
			Log.e(TAG, "onPreExecute() failed", e)
			errorMsg = e.localizedMessage
			cancel(true)
		}
	}

	override fun onPostExecute(result: Void?) {
		when (resultCode) {
			-1 -> {
				dialog?.dismiss()
				DialogUtils.showErrorDialog(
					activity,
					activity.resources.getString(R.string.osm_upload_error) + ": " + errorMsg
				)
			}
			okResultCode -> {
				dialog?.dismiss()
				DataHelper.setTrackUploadDate(trackId, System.currentTimeMillis(), activity.contentResolver)
				AlertDialog.Builder(activity)
					.setTitle(android.R.string.dialog_alert_title)
					.setIcon(android.R.drawable.ic_dialog_info)
					.setMessage(R.string.osm_upload_sucess)
					.setCancelable(true)
					.setNeutralButton(android.R.string.ok) { dialogInterface: DialogInterface, _ ->
						dialogInterface.dismiss()
						activity.finish()
					}.create().show()
			}
			authorizationErrorResultCode -> {
				dialog?.dismiss()
				AlertDialog.Builder(activity)
					.setTitle(android.R.string.dialog_alert_title)
					.setIcon(android.R.drawable.ic_dialog_alert)
					.setMessage(R.string.osm_upload_unauthorized)
					.setCancelable(true)
					.setNegativeButton(android.R.string.no) { d: DialogInterface, _ -> d.dismiss() }
					.setPositiveButton(android.R.string.yes) { d: DialogInterface, _ ->
						PreferenceManager.getDefaultSharedPreferences(activity)
							.edit()
							.remove(OSMTracker.Preferences.KEY_OSM_OAUTH2_ACCESSTOKEN)
							.apply()
						d.dismiss()
					}.create().show()
			}
			else -> {
				dialog?.dismiss()
				DialogUtils.showErrorDialog(
					activity,
					activity.resources.getString(R.string.osm_upload_error) + ": " + errorMsg
				)
			}
		}
	}

	override fun doInBackground(vararg params: Void?): Void? {
		val osm = OsmConnection(
			OpenStreetMapConstants.Api.OSM_API_URL_PATH,
			OpenStreetMapConstants.OAuth2.USER_AGENT,
			accessToken
		)
		val tagList = mutableListOf<String>()
		tagList.add(safeTags)
		try {
			FileInputStream(gpxFile).use { input ->
				val gpxId = GpsTracesApi(osm).create(
					filename,
					getVisibilityForOsmapi(safeVisibility),
					safeDescription,
					tagList,
					input
				)
				Log.v(TAG, "Gpx file uploaded. GPX id: $gpxId")
				resultCode = okResultCode
			}
		} catch (e: IOException) {
			Log.d(TAG, e.message ?: "IOException")
			resultCode = -1
		} catch (e: IllegalArgumentException) {
			Log.d(TAG, e.message ?: "IllegalArgumentException")
			resultCode = -1
		} catch (e: OsmBadUserInputException) {
			Log.d(TAG, e.message ?: "OsmBadUserInputException")
			resultCode = -1
		} catch (oae: OsmAuthorizationException) {
			Log.d(TAG, "OsmAuthorizationException")
			resultCode = authorizationErrorResultCode
		} catch (e: Exception) {
			Log.e(TAG, e.message ?: "Exception")
			resultCode = anotherErrorResultCode
		}
		return null
	}

	private fun getVisibilityForOsmapi(visibility: Track.OSMVisibility): GpsTraceDetails.Visibility {
		return when (visibility) {
			Track.OSMVisibility.Private -> GpsTraceDetails.Visibility.PRIVATE
			Track.OSMVisibility.Public -> GpsTraceDetails.Visibility.PUBLIC
			Track.OSMVisibility.Trackable -> GpsTraceDetails.Visibility.TRACKABLE
			Track.OSMVisibility.Identifiable -> GpsTraceDetails.Visibility.IDENTIFIABLE
		}
	}

	companion object {
		private val TAG = UploadToOpenStreetMapTask::class.java.simpleName
	}
}


