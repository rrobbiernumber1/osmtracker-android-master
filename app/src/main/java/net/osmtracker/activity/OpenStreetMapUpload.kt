package net.osmtracker.activity

import android.content.ContentUris
import android.content.Intent
import android.content.SharedPreferences
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenResponse
import net.osmtracker.OSMTracker
import net.osmtracker.R
import net.osmtracker.db.TrackContentProvider
import net.osmtracker.db.model.Track
import net.osmtracker.gpx.ExportToTempFileTask
import net.osmtracker.osm.OpenStreetMapConstants
import net.osmtracker.osm.UploadToOpenStreetMapTask

class OpenStreetMapUpload : TrackDetailEditor() {

	companion object {
		const val OAUTH2_CALLBACK_URL = "osmtracker://osm-upload/oath2-completed/?" + TrackContentProvider.Schema.COL_TRACK_ID + "="
		const val RC_AUTH = 7
		private val TAG = OpenStreetMapUpload::class.java.simpleName
	}

	private var authService: AuthorizationService? = null

	override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState, R.layout.osm_upload, obtainTrackId())
		fieldsMandatory = true
		findViewById<Button>(R.id.osm_upload_btn_ok).setOnClickListener {
			if (save()) startUpload()
		}
		findViewById<Button>(R.id.osm_upload_btn_cancel).setOnClickListener { finish() }
		window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
	}

    private fun obtainTrackId(): Long {
		return if (intent.extras != null && intent.extras!!.containsKey(TrackContentProvider.Schema.COL_TRACK_ID)) {
			intent.extras!!.getLong(TrackContentProvider.Schema.COL_TRACK_ID)
		} else if (intent.data != null && intent.data.toString().startsWith(OAUTH2_CALLBACK_URL)) {
			intent.data!!.getQueryParameter(TrackContentProvider.Schema.COL_TRACK_ID)!!.toLong()
		} else {
			throw IllegalArgumentException("Missing Track ID")
		}
	}

	override fun onResume() {
		super.onResume()
		val cursor: Cursor? = managedQuery(ContentUris.withAppendedId(TrackContentProvider.CONTENT_URI_TRACK, trackId), null, null, null, null)
		if (!cursor!!.moveToFirst()) {
			Toast.makeText(this, "Track ID not found.", Toast.LENGTH_SHORT).show()
			finish(); return
		}
		bindTrack(Track.build(trackId, cursor, contentResolver, false))
	}

	private fun startUpload() {
		val prefs = PreferenceManager.getDefaultSharedPreferences(this)
		if (prefs.contains(OSMTracker.Preferences.KEY_OSM_OAUTH2_ACCESSTOKEN)) {
			uploadToOsm(prefs.getString(OSMTracker.Preferences.KEY_OSM_OAUTH2_ACCESSTOKEN, "") ?: "")
		} else {
			requestOsmAuth()
		}
	}

	fun requestOsmAuth() {
		val serviceConfig = AuthorizationServiceConfiguration(
			Uri.parse(OpenStreetMapConstants.OAuth2.Urls.AUTHORIZATION_ENDPOINT),
			Uri.parse(OpenStreetMapConstants.OAuth2.Urls.TOKEN_ENDPOINT)
		)
		val redirectURI = Uri.parse(OAUTH2_CALLBACK_URL + trackId)
		val authRequest = AuthorizationRequest.Builder(
			serviceConfig,
			OpenStreetMapConstants.OAuth2.CLIENT_ID,
			ResponseTypeValues.CODE,
			redirectURI
		).setScope(OpenStreetMapConstants.OAuth2.SCOPE).build()
		authService = AuthorizationService(this)
		val authIntent = authService!!.getAuthorizationRequestIntent(authRequest)
		startActivityForResult(authIntent, RC_AUTH)
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		super.onActivityResult(requestCode, resultCode, data)
		if (requestCode == RC_AUTH) {
			val resp = AuthorizationResponse.fromIntent(data!!)
			val ex = AuthorizationException.fromIntent(data)
			if (ex != null) {
				Log.e(TAG, "Authorization Error. Exception received from server.")
				Log.e(TAG, ex.message ?: "")
			} else if (resp == null) {
				Log.e(TAG, "Authorization Error. Null response from server.")
			} else {
				val prefs = PreferenceManager.getDefaultSharedPreferences(this)
				authService!!.performTokenRequest(resp.createTokenExchangeRequest()) { tokenResp: TokenResponse?, ex2: AuthorizationException? ->
					if (tokenResp != null) {
						prefs.edit().putString(OSMTracker.Preferences.KEY_OSM_OAUTH2_ACCESSTOKEN, tokenResp.accessToken).apply()
						uploadToOsm(tokenResp.accessToken!!)
					} else {
						Log.e(TAG, "OAuth failed.")
					}
				}
			}
		} else {
			Log.e(TAG, "Unexpected requestCode:$requestCode.")
		}
	}

    fun uploadToOsm(accessToken: String) {
        object : ExportToTempFileTask(this@OpenStreetMapUpload, trackId) {
            override fun executionCompleted() {
                val tmp = this.getTmpFile()
                val name = this.getFilename() ?: tmp.name
                UploadToOpenStreetMapTask(
                    this@OpenStreetMapUpload,
                    accessToken,
                    trackId,
                    tmp,
                    name,
                    etDescription.text.toString(),
                    etTags.text.toString(),
                    Track.OSMVisibility.fromPosition(this@OpenStreetMapUpload.spVisibility.selectedItemPosition)
                ).execute()
            }
        }.execute()
    }
}


