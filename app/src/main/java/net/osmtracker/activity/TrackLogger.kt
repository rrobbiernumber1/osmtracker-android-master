package net.osmtracker.activity

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.*
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.database.Cursor
import android.location.LocationManager
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.StrictMode
import android.preference.PreferenceManager
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import net.osmtracker.OSMTracker
import net.osmtracker.R
import net.osmtracker.db.DataHelper
import net.osmtracker.db.TrackContentProvider
import net.osmtracker.layout.GpsStatusRecord
import net.osmtracker.listener.PressureListener
import net.osmtracker.listener.SensorListener
import net.osmtracker.service.gps.GPSLogger
import net.osmtracker.service.gps.GPSLoggerServiceConnection
import net.osmtracker.util.FileSystemUtils
import net.osmtracker.util.ThemeValidator
import net.osmtracker.view.TextNoteDialog
import java.io.File
import java.util.*

class TrackLogger : Activity() {

	private val TAG: String = TrackLogger::class.java.simpleName

    

	companion object {
		const val STATE_IS_TRACKING = "isTracking"
		const val TAG_SEPARATOR = ","
		const val STATE_BUTTONS_ENABLED = "buttonsEnabled"
		const val DIALOG_TEXT_NOTE = 1
	}

	private var gpsLogger: GPSLogger? = null
	private var gpsLoggerServiceIntent: Intent? = null
	private var checkGPSFlag: Boolean = true
    
	private var currentTrackId: Long = 0
	private val gpsLoggerConnection: ServiceConnection = GPSLoggerServiceConnection(this)
	private var prefs: SharedPreferences? = null
	private var buttonsEnabled: Boolean = false
	private lateinit var sensorListener: SensorListener
	private lateinit var pressureListener: PressureListener
    

	fun getButtonsEnabled(): Boolean = buttonsEnabled

	override fun onCreate(savedInstanceState: Bundle?) {
		currentTrackId = intent.extras!!.getLong(TrackContentProvider.Schema.COL_TRACK_ID)
		Log.v(TAG, "Starting for track id $currentTrackId")
        
		gpsLoggerServiceIntent = Intent(this, GPSLogger::class.java).apply {
			putExtra(TrackContentProvider.Schema.COL_TRACK_ID, currentTrackId)
		}
		PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
		prefs = PreferenceManager.getDefaultSharedPreferences(this)
		setTheme(resources.getIdentifier(ThemeValidator.getValidTheme(prefs!!, resources), null, null))
		super.onCreate(savedInstanceState)
		setContentView(R.layout.tracklogger)
		findViewById<View>(R.id.tracklogger_root).keepScreenOn = prefs!!.getBoolean(OSMTracker.Preferences.KEY_UI_DISPLAY_KEEP_ON, OSMTracker.Preferences.VAL_UI_DISPLAY_KEEP_ON)
		if (savedInstanceState != null) {
			buttonsEnabled = savedInstanceState.getBoolean(STATE_BUTTONS_ENABLED, false)
		}
		sensorListener = SensorListener()
		pressureListener = PressureListener()
        
	}

	private fun saveTagsForTrack() {
		val trackUri = ContentUris.withAppendedId(TrackContentProvider.CONTENT_URI_TRACK, currentTrackId)
		val values = ContentValues()
		val tagsToSave = HashSet<String>()
		val cursor = contentResolver.query(trackUri, null, null, null, null)
		val tagsIndex = cursor!!.getColumnIndex(TrackContentProvider.Schema.COL_TAGS)
		var previouslySavedTags: String? = null
		while (cursor.moveToNext()) {
			if (cursor.getString(tagsIndex) != null) previouslySavedTags = cursor.getString(tagsIndex)
		}
		if (previouslySavedTags != null) {
			for (tag in previouslySavedTags.split(TAG_SEPARATOR).toTypedArray()) tagsToSave.add(tag)
		}
		tagsToSave.add("osmtracker")
		val tagsString = StringBuilder()
		for (tag in tagsToSave) tagsString.append(tag).append(TAG_SEPARATOR)
		val lastIndex = tagsString.length - 1
		if (lastIndex >= 0) tagsString.deleteCharAt(lastIndex)
		values.put(TrackContentProvider.Schema.COL_TAGS, tagsString.toString())
		contentResolver.update(trackUri, values, null, null)
	}

	override fun onResume() {
		title = resources.getString(R.string.tracklogger) + ": #" + currentTrackId
		findViewById<View>(R.id.tracklogger_root).keepScreenOn = prefs!!.getBoolean(OSMTracker.Preferences.KEY_UI_DISPLAY_KEEP_ON, OSMTracker.Preferences.VAL_UI_DISPLAY_KEEP_ON)
		val preferredOrientation = prefs!!.getString(OSMTracker.Preferences.KEY_UI_ORIENTATION, OSMTracker.Preferences.VAL_UI_ORIENTATION)
		if (OSMTracker.Preferences.VAL_UI_ORIENTATION_PORTRAIT == preferredOrientation) {
			requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
		} else if (OSMTracker.Preferences.VAL_UI_ORIENTATION_LANDSCAPE == preferredOrientation) {
			requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
		} else {
			requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
		}
		
		if (checkGPSFlag && prefs!!.getBoolean(OSMTracker.Preferences.KEY_GPS_CHECKSTARTUP, OSMTracker.Preferences.VAL_GPS_CHECKSTARTUP)) {
			checkGPSProvider()
		}
		(findViewById<View>(R.id.gpsStatus) as GpsStatusRecord).requestLocationUpdates(true)
		startService(gpsLoggerServiceIntent)
        bindService(gpsLoggerServiceIntent!!, gpsLoggerConnection, 0)
        sensorListener.register(this)
        pressureListener.register(this, prefs!!.getBoolean(OSMTracker.Preferences.KEY_USE_BAROMETER, OSMTracker.Preferences.VAL_USE_BAROMETER))
        setEnabledActionButtons(buttonsEnabled)
        if (!buttonsEnabled) Toast.makeText(this, R.string.tracklogger_waiting_gps, Toast.LENGTH_LONG).show()

		// If launched from TrackManager's left-bottom FAB, open TextNote dialog immediately
		if (intent?.categories?.contains("OPEN_TEXT_NOTE") == true) {
			showDialog(DIALOG_TEXT_NOTE)
			intent?.categories?.remove("OPEN_TEXT_NOTE")
		}
		super.onResume()
	}

	private fun checkGPSProvider() {
		val lm = getSystemService(LOCATION_SERVICE) as LocationManager
		if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
			AlertDialog.Builder(this)
				.setTitle(R.string.tracklogger_gps_disabled)
				.setIcon(android.R.drawable.ic_dialog_alert)
				.setMessage(resources.getString(R.string.tracklogger_gps_disabled_hint))
				.setCancelable(true)
				.setPositiveButton(android.R.string.yes) { _: DialogInterface, _: Int -> startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
				.setNegativeButton(android.R.string.no) { dialog: DialogInterface, _: Int -> dialog.cancel() }
				.create().show()
			checkGPSFlag = false
		}
	}

	override fun onPause() {
		(findViewById<View>(R.id.gpsStatus) as GpsStatusRecord).requestLocationUpdates(false)
		gpsLogger?.let { logger ->
			if (!logger.isTracking()) {
				Log.v(TAG, "Service is not tracking, trying to stopService()")
				unbindService(gpsLoggerConnection)
				stopService(gpsLoggerServiceIntent)
			} else {
				unbindService(gpsLoggerConnection)
			}
		}
		sensorListener.unregister()
		pressureListener.unregister()
		super.onPause()
	}

	override fun onSaveInstanceState(outState: Bundle) {
		if (gpsLogger != null) outState.putBoolean(STATE_IS_TRACKING, gpsLogger!!.isTracking())
		outState.putBoolean(STATE_BUTTONS_ENABLED, buttonsEnabled)
		super.onSaveInstanceState(outState)
	}

	fun onGpsDisabled() { setEnabledActionButtons(false) }

	fun onGpsEnabled() { if (gpsLogger != null && gpsLogger!!.isTracking()) setEnabledActionButtons(true) }

    fun setEnabledActionButtons(enabled: Boolean) { buttonsEnabled = enabled }

	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		val inflater: MenuInflater = menuInflater
		inflater.inflate(R.menu.tracklogger_menu, menu)
		return true
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		var i: Intent
		when (item.itemId) {
			R.id.tracklogger_menu_stoptracking -> {
				if (gpsLogger!!.isTracking()) {
					saveTagsForTrack()
					val intent = Intent(OSMTracker.INTENT_STOP_TRACKING)
					intent.setPackage(packageName)
					sendBroadcast(intent)
					(findViewById<View>(R.id.gpsStatus) as GpsStatusRecord).manageRecordingIndicator(false)
					finish()
				}
			}
			R.id.tracklogger_menu_settings -> startActivity(Intent(this, Preferences::class.java))
			R.id.tracklogger_menu_waypointlist -> {
				i = Intent(this, WaypointList::class.java)
				i.putExtra(TrackContentProvider.Schema.COL_TRACK_ID, currentTrackId)
				startActivity(i)
			}
			// About 기능 제거됨
			R.id.tracklogger_menu_displaytrack -> {
				val useOpenStreetMapBackground = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
					OSMTracker.Preferences.KEY_UI_DISPLAYTRACK_OSM, OSMTracker.Preferences.VAL_UI_DISPLAYTRACK_OSM
				)
				i = if (useOpenStreetMapBackground) Intent(this, DisplayTrackMap::class.java) else Intent(this, DisplayTrack::class.java)
				i.putExtra(TrackContentProvider.Schema.COL_TRACK_ID, currentTrackId)
				startActivity(i)
			}
		}
		return super.onOptionsItemSelected(item)
	}

	override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean { return super.onKeyDown(keyCode, event) }

	fun requestStillImage() { }

	fun getRealPathFromURI(contentUri: Uri): String {
		val cursor = contentResolver.query(contentUri, null, null, null, null)
		val column_index = cursor!!.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.DATA)
		cursor.moveToFirst()
		return cursor.getString(column_index)
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		Log.v(TAG, "Activity result: $requestCode, resultCode=$resultCode, Intent=$data")
		super.onActivityResult(requestCode, resultCode, data)
	}

	fun getGpsLogger(): GPSLogger? = gpsLogger
	fun setGpsLogger(l: GPSLogger?) { gpsLogger = l }

	private fun createImageFile(): File? { return null }

	override fun onCreateDialog(id: Int): Dialog? {
		when (id) {
			DIALOG_TEXT_NOTE -> return TextNoteDialog(this, currentTrackId)
		}
		return super.onCreateDialog(id)
	}

	override fun onPrepareDialog(id: Int, dialog: Dialog) {
		when (id) {
			DIALOG_TEXT_NOTE -> (dialog as TextNoteDialog).resetValues()
		}
		super.onPrepareDialog(id, dialog)
	}

	override fun onNewIntent(newIntent: Intent) {
		if (newIntent.extras != null) {
			if (newIntent.extras!!.containsKey(TrackContentProvider.Schema.COL_TRACK_ID)) {
				currentTrackId = newIntent.extras!!.getLong(TrackContentProvider.Schema.COL_TRACK_ID)
				intent = newIntent
			}
		}
		super.onNewIntent(newIntent)
	}

	fun getCurrentTrackId(): Long = currentTrackId

	private fun startCamera() { }

	private fun startGallery() { }

	override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults)
	}
}


