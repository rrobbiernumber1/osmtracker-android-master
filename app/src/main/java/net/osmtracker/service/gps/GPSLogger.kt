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
import net.osmtracker.activity.TrackManager
import net.osmtracker.db.DataHelper
import net.osmtracker.db.TrackContentProvider
import android.os.Looper

class GPSLogger : Service(), LocationListener {

	private val TAG: String = GPSLogger::class.java.simpleName

	private lateinit var dataHelper: DataHelper
	private var isTracking: Boolean = false
	private var isGpsEnabled: Boolean = false
	private var lastLocation: Location? = null
	private lateinit var lmgr: LocationManager
	private var currentTrackId: Long = -1
	private var lastGPSTimestamp: Long = 0
	private var gpsLoggingInterval: Long = 0
	private var gpsLoggingMinDistance: Long = 0
	
	// Timer for periodic tracking even when GPS signal is weak
	private var trackingTimer: android.os.Handler? = null
	private var trackingRunnable: Runnable? = null

	private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
		override fun onReceive(context: Context, intent: Intent) {
			Log.d(TAG, "BroadcastReceiver received intent: ${intent.action}")

			when (intent.action) {
				OSMTracker.INTENT_TRACK_WP -> {
					val extras = intent.extras
					if (extras != null) {
						if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
							// GPS 신호 강도와 관계없이 실내/실외에서 모두 측정 가능하도록 수정
							val trackId = extras.getLong(TrackContentProvider.Schema.COL_TRACK_ID)
							val uuid = extras.getString(OSMTracker.INTENT_KEY_UUID)
							val name = extras.getString(OSMTracker.INTENT_KEY_NAME)
							val link = extras.getString(OSMTracker.INTENT_KEY_LINK)

							// lastLocation이 있으면 사용하고, 없으면 null로 waypoint 추가
							val location = lastLocation ?: lmgr.getLastKnownLocation(LocationManager.GPS_PROVIDER)
							dataHelper.wayPoint(trackId, location, name ?: "", link, uuid)
							
							// location이 있으면 track point도 추가
							location?.let { loc ->
								dataHelper.track(trackId, loc)
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
						Log.d(TAG, "Received START_TRACKING intent for track ID: $trackId")
						startTracking(trackId)
					} else {
						Log.w(TAG, "START_TRACKING intent received without extras")
					}
				}
				OSMTracker.INTENT_STOP_TRACKING -> {
					Log.d(TAG, "Received STOP_TRACKING intent")
					stopTrackingAndSave()
				}
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

		Log.d(TAG, "GPS logging interval: ${gpsLoggingInterval}ms, min distance: ${gpsLoggingMinDistance}m")

		// BroadcastReceiver 등록
		val filter = IntentFilter()
		filter.addAction(OSMTracker.INTENT_TRACK_WP)
		filter.addAction(OSMTracker.INTENT_UPDATE_WP)
		filter.addAction(OSMTracker.INTENT_DELETE_WP)
		filter.addAction(OSMTracker.INTENT_START_TRACKING)
		filter.addAction(OSMTracker.INTENT_STOP_TRACKING)
		
		try {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
				registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
			} else {
				registerReceiver(receiver, filter)
			}
			Log.d(TAG, "BroadcastReceiver registered successfully")
		} catch (e: Exception) {
			Log.e(TAG, "Failed to register BroadcastReceiver", e)
		}

		// LocationManager 설정
		lmgr = getSystemService(Context.LOCATION_SERVICE) as LocationManager
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
			try {
				lmgr.requestLocationUpdates(LocationManager.GPS_PROVIDER, gpsLoggingInterval, gpsLoggingMinDistance.toFloat(), this)
				Log.d(TAG, "Location updates requested successfully")
			} catch (e: Exception) {
				Log.e(TAG, "Failed to request location updates", e)
			}
		} else {
			Log.w(TAG, "GPS permission not granted")
		}

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
			Log.d(TAG, "Stopping active tracking before service destruction")
			stopTrackingAndSave()
		}
		// Stop periodic tracking timer
		stopPeriodicTracking()
		
		// Remove location updates
		try {
			lmgr.removeUpdates(this)
			Log.d(TAG, "Location updates removed")
		} catch (e: Exception) {
			Log.e(TAG, "Error removing location updates", e)
		}
		
		// Unregister BroadcastReceiver
		try {
			unregisterReceiver(receiver)
			Log.d(TAG, "BroadcastReceiver unregistered")
		} catch (e: Exception) {
			Log.e(TAG, "Error unregistering BroadcastReceiver", e)
		}
		
		stopNotifyBackgroundService()
		super.onDestroy()
	}

	private fun startTracking(trackId: Long) {
		currentTrackId = trackId
		Log.d(TAG, "Starting track logging for track #$trackId")
		val nmgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
		nmgr.notify(NOTIFICATION_ID, notification)
		isTracking = true
		
		// 즉시 마지막 알려진 위치가 있으면 첫 번째 포인트로 기록
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
			try {
				val lastKnownLocation = lmgr.getLastKnownLocation(LocationManager.GPS_PROVIDER)
				if (lastKnownLocation != null) {
					Log.d(TAG, "Recording initial last known location for track #$trackId")
					dataHelper.track(trackId, lastKnownLocation)
					lastLocation = lastKnownLocation
				} else {
					Log.d(TAG, "No last known location available, creating indoor entry for track #$trackId")
					createIndoorLocationEntry()
				}
			} catch (e: Exception) {
				Log.w(TAG, "Error getting last known location", e)
				createIndoorLocationEntry()
			}
		}
		
		// Start periodic tracking timer for continuous tracking
		startPeriodicTracking()

		Log.d(TAG, "Track logging started successfully for track #$trackId")
	}
	
	private fun startPeriodicTracking() {
		// Stop existing timer if any
		stopPeriodicTracking()
		
		trackingTimer = android.os.Handler(Looper.getMainLooper())
		trackingRunnable = object : Runnable {
			override fun run() {
				if (isTracking && currentTrackId > 0) {
					Log.d(TAG, "Periodic tracking check for track #$currentTrackId")
					
					// Try to get current location, even if weak
					if (ContextCompat.checkSelfPermission(this@GPSLogger, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
						try {
							val currentLocation = lmgr.getLastKnownLocation(LocationManager.GPS_PROVIDER)
							if (currentLocation != null) {
								// Use current GPS location
								Log.d(TAG, "Recording GPS location: lat=${currentLocation.latitude}, lon=${currentLocation.longitude}")
								dataHelper.track(currentTrackId, currentLocation)
								lastLocation = currentLocation
							} else if (lastLocation != null) {
								// Use last known location if no current fix
								Log.d(TAG, "Recording last known location: lat=${lastLocation!!.latitude}, lon=${lastLocation!!.longitude}")
								dataHelper.track(currentTrackId, lastLocation!!)
							} else {
								// Create a basic location entry for indoor tracking
								Log.d(TAG, "No GPS signal, creating indoor location entry")
								createIndoorLocationEntry()
							}
						} catch (e: Exception) {
							Log.e(TAG, "Error in periodic tracking", e)
							createIndoorLocationEntry()
						}
					}
					
					// Schedule next tracking
					trackingTimer?.postDelayed(this, gpsLoggingInterval)
				} else {
					Log.d(TAG, "Stopping periodic tracking - isTracking: $isTracking, currentTrackId: $currentTrackId")
				}
			}
		}
		
		// Start the timer
		trackingTimer?.postDelayed(trackingRunnable!!, gpsLoggingInterval)
	}
	
	private fun stopPeriodicTracking() {
		trackingRunnable?.let { runnable ->
			trackingTimer?.removeCallbacks(runnable)
		}
		trackingTimer = null
		trackingRunnable = null
	}
	
	private fun createIndoorLocationEntry() {
		// Create a basic location entry for indoor tracking when no GPS signal
		val indoorLocation = Location(LocationManager.GPS_PROVIDER)
		indoorLocation.latitude = 0.0
		indoorLocation.longitude = 0.0
		indoorLocation.time = System.currentTimeMillis()
		indoorLocation.accuracy = 1000.0f // High uncertainty for indoor
		
		dataHelper.track(currentTrackId, indoorLocation)
	}

	private fun stopTrackingAndSave() {
		isTracking = false
		// Stop periodic tracking timer
		stopPeriodicTracking()
		dataHelper.stopTracking(currentTrackId)
		currentTrackId = -1
		stopSelf()
	}

	override fun onLocationChanged(location: Location) {
		isGpsEnabled = true
		lastLocation = location

		Log.d("rrobbie", "onLocationChanged : ${isTracking}")
		
		// Always record location when GPS signal is available, regardless of interval
		if (isTracking) {
			// Check if enough time has passed since last recording
			if (lastGPSTimestamp + gpsLoggingInterval < System.currentTimeMillis()) {
				lastGPSTimestamp = System.currentTimeMillis()
				dataHelper.track(currentTrackId, location)
			}
		}
	}

	private val notification: Notification
		get() {
			val startTrackManager = Intent(this, TrackManager::class.java)
			startTrackManager.putExtra(TrackContentProvider.Schema.COL_TRACK_ID, currentTrackId)
			val contentIntent = PendingIntent.getActivity(this, 0, startTrackManager, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
			val builder = NotificationCompat.Builder(this, CHANNEL_ID)
				.setSmallIcon(R.drawable.ic_launcher)
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


