package net.osmtracker.service.gps

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.preference.PreferenceManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import net.osmtracker.OSMTracker
import net.osmtracker.R
import net.osmtracker.activity.TrackLogger
import net.osmtracker.db.DataHelper
import net.osmtracker.db.TrackContentProvider
import net.osmtracker.listener.PressureListener
import net.osmtracker.listener.SensorListener

class GPSLogger : Service(), LocationListener {

	private val TAG: String = GPSLogger::class.java.simpleName

	private lateinit var dataHelper: DataHelper
	private var isTracking: Boolean = false
	private var isGpsEnabled: Boolean = false
	private var use_barometer: Boolean = false
	private var lastLocation: Location? = null
	private lateinit var lmgr: LocationManager
	private var currentTrackId: Long = -1
	private var lastGPSTimestamp: Long = 0
	private var gpsLoggingInterval: Long = 0
	private var gpsLoggingMinDistance: Long = 0
	private val sensorListener: SensorListener = SensorListener()
	private val pressureListener: PressureListener = PressureListener()

	private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
		override fun onReceive(context: Context, intent: Intent) {
			Log.v(TAG, "Received intent ${intent.action}")

			when (intent.action) {
				OSMTracker.INTENT_TRACK_WP -> {
					val extras = intent.extras
					if (extras != null) {
						if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
							lastLocation = lmgr.getLastKnownLocation(LocationManager.GPS_PROVIDER)
							lastLocation?.let { loc ->
								val trackId = extras.getLong(TrackContentProvider.Schema.COL_TRACK_ID)
								val uuid = extras.getString(OSMTracker.INTENT_KEY_UUID)
								val name = extras.getString(OSMTracker.INTENT_KEY_NAME)
								val link = extras.getString(OSMTracker.INTENT_KEY_LINK)

								dataHelper.wayPoint(trackId, loc, name ?: "", link, uuid, sensorListener.getAzimuth(), sensorListener.getAccuracy(), pressureListener.getPressure())
								dataHelper.track(currentTrackId, loc, sensorListener.getAzimuth(), sensorListener.getAccuracy(), pressureListener.getPressure())
							}
						}
					}
				}
				OSMTracker.INTENT_UPDATE_WP -> {
					val extras = intent.extras
					if (extras != null) {
						val trackId = extras.getLong(TrackContentProvider.Schema.COL_TRACK_ID)
						val uuid = extras.getString(OSMTracker.INTENT_KEY_UUID)
						val name = extras.getString(OSMTracker.INTENT_KEY_NAME)
						val link = extras.getString(OSMTracker.INTENT_KEY_LINK)
						dataHelper.updateWayPoint(trackId, uuid, name, link)
					}
				}
				OSMTracker.INTENT_DELETE_WP -> {
					val extras = intent.extras
					if (extras != null) {
						val trackId = extras.getLong(TrackContentProvider.Schema.COL_TRACK_ID)
						val uuid = extras.getString(OSMTracker.INTENT_KEY_UUID)
						val link = extras.getString(OSMTracker.INTENT_KEY_LINK)
						var filePath: String? = null
						try {
							filePath = if ("null" == link) null else DataHelper.getTrackDirectory(trackId, context).toString() + "/" + link
						} catch (ne: NullPointerException) {
							// ignore
						}
						dataHelper.deleteWayPoint(uuid, filePath)
					}
				}
				OSMTracker.INTENT_START_TRACKING -> {
					val extras = intent.extras
					if (extras != null) {
						val trackId = extras.getLong(TrackContentProvider.Schema.COL_TRACK_ID)
						startTracking(trackId)
					}
				}
				OSMTracker.INTENT_STOP_TRACKING -> stopTrackingAndSave()
			}
		}
	}

	private val binder: IBinder = GPSLoggerBinder()

	override fun onBind(intent: Intent): IBinder {
		Log.v(TAG, "Service onBind()")
		return binder
	}

	override fun onUnbind(intent: Intent): Boolean {
		Log.v(TAG, "Service onUnbind()")
		if (!isTracking) {
			Log.v(TAG, "Service self-stopping")
			stopSelf()
		}
		return false
	}

	inner class GPSLoggerBinder : Binder() {
		fun getService(): GPSLogger {
			return this@GPSLogger
		}
	}

	override fun onCreate() {
		Log.v(TAG, "Service onCreate()")
		dataHelper = DataHelper(this)

		gpsLoggingInterval = PreferenceManager.getDefaultSharedPreferences(this.applicationContext)
			.getString(OSMTracker.Preferences.KEY_GPS_LOGGING_INTERVAL, OSMTracker.Preferences.VAL_GPS_LOGGING_INTERVAL)!!.toLong() * 1000
		gpsLoggingMinDistance = PreferenceManager.getDefaultSharedPreferences(this.applicationContext)
			.getString(OSMTracker.Preferences.KEY_GPS_LOGGING_MIN_DISTANCE, OSMTracker.Preferences.VAL_GPS_LOGGING_MIN_DISTANCE)!!.toLong()
		use_barometer = PreferenceManager.getDefaultSharedPreferences(this.applicationContext)
			.getBoolean(OSMTracker.Preferences.KEY_USE_BAROMETER, OSMTracker.Preferences.VAL_USE_BAROMETER)

		val filter = IntentFilter()
		filter.addAction(OSMTracker.INTENT_TRACK_WP)
		filter.addAction(OSMTracker.INTENT_UPDATE_WP)
		filter.addAction(OSMTracker.INTENT_DELETE_WP)
		filter.addAction(OSMTracker.INTENT_START_TRACKING)
		filter.addAction(OSMTracker.INTENT_STOP_TRACKING)
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
		} else {
			registerReceiver(receiver, filter)
		}

		lmgr = getSystemService(Context.LOCATION_SERVICE) as LocationManager
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
			lmgr.requestLocationUpdates(LocationManager.GPS_PROVIDER, gpsLoggingInterval, gpsLoggingMinDistance.toFloat(), this)
		}

		sensorListener.register(this)
		pressureListener.register(this, use_barometer)

		super.onCreate()
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		Log.v(TAG, "Service onStartCommand(-,$flags,$startId)")
		createNotificationChannel()
		startForeground(NOTIFICATION_ID, notification)
		return START_STICKY
	}

	override fun onDestroy() {
		Log.v(TAG, "Service onDestroy()")
		if (isTracking) {
			stopTrackingAndSave()
		}
		lmgr.removeUpdates(this)
		unregisterReceiver(receiver)
		stopNotifyBackgroundService()
		sensorListener.unregister()
		pressureListener.unregister()
		super.onDestroy()
	}

	private fun startTracking(trackId: Long) {
		currentTrackId = trackId
		Log.v(TAG, "Starting track logging for track #$trackId")
		val nmgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
		nmgr.notify(NOTIFICATION_ID, notification)
		isTracking = true
	}

	private fun stopTrackingAndSave() {
		isTracking = false
		dataHelper.stopTracking(currentTrackId)
		currentTrackId = -1
		stopSelf()
	}

	override fun onLocationChanged(location: Location) {
		isGpsEnabled = true
		if (lastGPSTimestamp + gpsLoggingInterval < System.currentTimeMillis()) {
			lastGPSTimestamp = System.currentTimeMillis()
			lastLocation = location
			if (isTracking) {
				dataHelper.track(currentTrackId, location, sensorListener.getAzimuth(), sensorListener.getAccuracy(), pressureListener.getPressure())
			}
		}
	}

	private val notification: Notification
		get() {
			val startTrackLogger = Intent(this, TrackLogger::class.java)
			startTrackLogger.putExtra(TrackContentProvider.Schema.COL_TRACK_ID, currentTrackId)
			val contentIntent = PendingIntent.getActivity(this, 0, startTrackLogger, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
			val builder = NotificationCompat.Builder(this, CHANNEL_ID)
				.setSmallIcon(R.drawable.ic_stat_track)
				.setContentTitle(resources.getString(R.string.notification_title).replace("{0}", if (currentTrackId > -1) currentTrackId.toString() else "?"))
				.setContentText(resources.getString(R.string.notification_text))
				.setPriority(NotificationCompat.PRIORITY_DEFAULT)
				.setContentIntent(contentIntent)
				.setAutoCancel(true)
			return builder.build()
		}

	private fun createNotificationChannel() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val name: CharSequence = "GPS Logger"
			val description = "Display when tracking in Background"
			val importance = NotificationManager.IMPORTANCE_LOW
			val channel = NotificationChannel(CHANNEL_ID, name, importance)
			channel.description = description
			val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
			notificationManager.createNotificationChannel(channel)
		}
	}

	private fun stopNotifyBackgroundService() {
		val nmgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
		nmgr.cancel(NOTIFICATION_ID)
	}

	override fun onProviderDisabled(provider: String) {
		isGpsEnabled = false
	}

	override fun onProviderEnabled(provider: String) {
		isGpsEnabled = true
	}

	override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {
		// Not interested in provider status
	}

	fun isGpsEnabled(): Boolean {
		return isGpsEnabled
	}

	fun isTracking(): Boolean {
		return isTracking
	}

	companion object {
		private const val NOTIFICATION_ID = 1
		private var CHANNEL_ID = "GPSLogger_Channel"
	}
}


