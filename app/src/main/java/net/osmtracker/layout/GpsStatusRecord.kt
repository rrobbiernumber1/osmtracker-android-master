package net.osmtracker.layout

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationProvider
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import net.osmtracker.OSMTracker
import net.osmtracker.R
import net.osmtracker.activity.TrackLogger
import java.text.DecimalFormat

class GpsStatusRecord(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs), LocationListener {

	private val REQUEST_CODE_GPS_PERMISSIONS = 1
	private val gpsLoggingInterval: Long
	private var activity: TrackLogger? = null
	private var lmgr: LocationManager? = null
	private var lastGPSTimestampStatus: Long = 0
	private var lastGPSTimestampLocation: Long = 0
	private var gpsActive: Boolean = false
	private var satCount: Int = 0
	private var fixCount: Int = 0

	init {
		LayoutInflater.from(context).inflate(R.layout.gpsstatus_record, this, true)
		gpsLoggingInterval = PreferenceManager.getDefaultSharedPreferences(context)
			.getString(OSMTracker.Preferences.KEY_GPS_LOGGING_INTERVAL, OSMTracker.Preferences.VAL_GPS_LOGGING_INTERVAL)
			?.toLong()?.times(1000) ?: 0L
		if (context is TrackLogger) {
			activity = context
			lmgr = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
		}
		findViewById<TextView>(R.id.gpsstatus_record_tvAccuracy).text = resources.getString(R.string.various_waiting_gps_fix)
			.replace("{0}", "0").replace("{1}", "0")
	}

	fun requestLocationUpdates(request: Boolean) {
		val act = activity ?: return
		val mgr = lmgr ?: return
		if (request) {
			if (ContextCompat.checkSelfPermission(act, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
				mgr.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0f, this)
				mgr.registerGnssStatusCallback(statusCallback)
			} else {
				ActivityCompat.requestPermissions(act, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_CODE_GPS_PERMISSIONS)
			}
		} else {
			mgr.removeUpdates(this)
			mgr.unregisterGnssStatusCallback(statusCallback)
		}
	}

	private val statusCallback: GnssStatus.Callback = object : GnssStatus.Callback() {
		override fun onSatelliteStatusChanged(status: GnssStatus) {
			satCount = status.satelliteCount
			fixCount = 0
			for (i in 0 until satCount) if (status.usedInFix(i)) fixCount++
			if (fixCount == 0) {
				findViewById<TextView>(R.id.gpsstatus_record_tvAccuracy).text = resources.getString(R.string.various_waiting_gps_fix)
					.replace("{0}", fixCount.toString()).replace("{1}", satCount.toString())
				findViewById<ImageView>(R.id.gpsstatus_record_imgSatIndicator).setImageResource(R.drawable.sat_indicator_unknown)
			}
			Log.v(TAG, "Found $satCount satellites. $fixCount used in fix.")
		}
	}

	override fun onLocationChanged(location: Location) {
		if (lastGPSTimestampLocation + gpsLoggingInterval < System.currentTimeMillis()) {
			lastGPSTimestampLocation = System.currentTimeMillis()
			Log.v(TAG, "Location received $location")
			val act = activity ?: return
			if (!gpsActive) {
				gpsActive = true
				act.onGpsEnabled()
				manageRecordingIndicator(true)
			} else if (gpsActive && !(act.getButtonsEnabled())) {
				act.onGpsEnabled()
				manageRecordingIndicator(true)
			}
			val tvAccuracy = findViewById<TextView>(R.id.gpsstatus_record_tvAccuracy)
			if (location.hasAccuracy()) {
				Log.d(TAG, "location accuracy: ${ACCURACY_FORMAT.format(location.accuracy)}")
				tvAccuracy.text = resources.getString(R.string.various_accuracy_with_sats)
					.replace("{0}", ACCURACY_FORMAT.format(location.accuracy))
					.replace("{1}", resources.getString(R.string.various_unit_meters))
					.replace("{2}", fixCount.toString())
					.replace("{3}", satCount.toString())
				manageSatelliteStatusIndicator(location.accuracy.toInt())
			} else {
				Log.d(TAG, "location without accuracy")
				tvAccuracy.text = ""
			}
		}
	}

	override fun onProviderDisabled(provider: String) {
		Log.d(TAG, "Location provider $provider disabled")
		gpsActive = false
		findViewById<ImageView>(R.id.gpsstatus_record_imgSatIndicator).setImageResource(R.drawable.sat_indicator_off)
		findViewById<TextView>(R.id.gpsstatus_record_tvAccuracy).text = ""
		activity?.onGpsDisabled()
	}

	override fun onProviderEnabled(provider: String) {
		Log.d(TAG, "Location provider $provider enabled")
		findViewById<ImageView>(R.id.gpsstatus_record_imgSatIndicator).setImageResource(R.drawable.sat_indicator_unknown)
	}

	override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {
		Log.d(TAG, "Location provider $provider status changed to: $status")
		val imgSatIndicator = findViewById<ImageView>(R.id.gpsstatus_record_imgSatIndicator)
		val tvAccuracy = findViewById<TextView>(R.id.gpsstatus_record_tvAccuracy)
		when (status) {
			LocationProvider.OUT_OF_SERVICE -> {
				imgSatIndicator.setImageResource(R.drawable.sat_indicator_off)
				tvAccuracy.text = ""
				gpsActive = false
				activity?.onGpsDisabled()
			}
			LocationProvider.TEMPORARILY_UNAVAILABLE -> {
				imgSatIndicator.setImageResource(R.drawable.sat_indicator_unknown)
				tvAccuracy.text = ""
				gpsActive = false
			}
		}
	}

	fun manageRecordingIndicator(isTracking: Boolean) {
		val recordStatus = findViewById<ImageView>(R.id.gpsstatus_record_animRec)
		recordStatus.setImageResource(if (isTracking) R.drawable.record_red else R.drawable.record_grey)
	}

	private fun manageSatelliteStatusIndicator(accuracy: Int) {
		val imgSatIndicator = findViewById<ImageView>(R.id.gpsstatus_record_imgSatIndicator)
		val nbBars = accuracy / 4
		when (nbBars) {
			0 -> imgSatIndicator.setImageResource(R.drawable.sat_indicator_5)
			1 -> imgSatIndicator.setImageResource(R.drawable.sat_indicator_4)
			2 -> imgSatIndicator.setImageResource(R.drawable.sat_indicator_3)
			3 -> imgSatIndicator.setImageResource(R.drawable.sat_indicator_2)
			4 -> imgSatIndicator.setImageResource(R.drawable.sat_indicator_1)
			else -> imgSatIndicator.setImageResource(R.drawable.sat_indicator_0)
		}
	}

	companion object {
		private val ACCURACY_FORMAT = DecimalFormat("0")
		private const val TAG = "GpsStatusRecord"
	}
}


