package net.osmtracker.service.gps

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import net.osmtracker.OSMTracker
import net.osmtracker.R
import net.osmtracker.activity.TrackLogger
import net.osmtracker.db.TrackContentProvider
import net.osmtracker.layout.GpsStatusRecord

class GPSLoggerServiceConnection(private val activity: TrackLogger) : ServiceConnection {

	override fun onServiceDisconnected(name: ComponentName) {
		activity.setEnabledActionButtons(false)
		activity.setGpsLogger(null)
	}

	override fun onServiceConnected(name: ComponentName, service: IBinder) {
		activity.setGpsLogger((service as GPSLogger.GPSLoggerBinder).getService())

		val gpsStatusRecord = activity.findViewById(R.id.gpsStatus) as GpsStatusRecord?
		if (gpsStatusRecord != null) {
			gpsStatusRecord.manageRecordingIndicator(activity.getGpsLogger().isTracking())
		}

		if (!activity.getGpsLogger().isTracking()) {
			activity.setEnabledActionButtons(false)
			val intent = Intent(OSMTracker.INTENT_START_TRACKING)
			intent.putExtra(TrackContentProvider.Schema.COL_TRACK_ID, activity.getCurrentTrackId())
			intent.setPackage(activity.packageName)
			activity.sendBroadcast(intent)
		}
	}
}


