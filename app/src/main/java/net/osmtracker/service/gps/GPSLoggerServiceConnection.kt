package net.osmtracker.service.gps

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import net.osmtracker.OSMTracker
import net.osmtracker.R
import net.osmtracker.activity.TrackManager
import net.osmtracker.db.DataHelper
import net.osmtracker.db.TrackContentProvider
import net.osmtracker.layout.GpsStatusRecord

class GPSLoggerServiceConnection(private val activity: Any) : ServiceConnection {

		override fun onServiceDisconnected(name: ComponentName) {
			when (activity) {
				is TrackManager -> {}
			}
		}

		override fun onServiceConnected(name: ComponentName, service: IBinder) {
			val logger = (service as GPSLogger.GPSLoggerBinder).getService()
			when (activity) {
				is TrackManager -> {
					val gpsStatusRecord = activity.findViewById(R.id.gpsStatus) as GpsStatusRecord?
					if (gpsStatusRecord != null) {
						gpsStatusRecord.manageRecordingIndicator(logger.isTracking())
					}

					// 바인드 시 tracking이 꺼져 있고 활성 트랙이 있으면 자동으로 시작
					if (!logger.isTracking()) {
						val activeId = DataHelper.getActiveTrackId(activity.contentResolver)
						if (activeId != -1L) {
							val intent = Intent(OSMTracker.INTENT_START_TRACKING)
							intent.putExtra(TrackContentProvider.Schema.COL_TRACK_ID, activeId)
							intent.setPackage(activity.packageName)
							activity.sendBroadcast(intent)
						}
					}
				}
			}
		}
}


