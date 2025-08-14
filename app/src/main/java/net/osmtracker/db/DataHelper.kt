package net.osmtracker.db

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.location.Location
import android.net.Uri
import android.preference.PreferenceManager
import android.util.Log
import net.osmtracker.OSMTracker
import net.osmtracker.db.model.Track
import net.osmtracker.db.model.TrackPoint
import net.osmtracker.db.model.WayPoint
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

open class DataHelper(private val context: Context) {

	companion object {
		private val TAG: String = DataHelper::class.java.simpleName
		const val EXTENSION_GPX = ".gpx"
		const val EXTENSION_3GPP = ".3gpp"
		const val EXTENSION_JPG = ".jpg"
		const val EXTENSION_ZIP = ".zip"
		const val MIME_TYPE_GPX = "application/gpx+xml"
		const val MIME_TYPE_AUDIO = "audio/*"
		const val MIME_TYPE_IMAGE = "image/*"
		const val FILE_PROVIDER_AUTHORITY = "net.osmtracker.fileprovider"
		private const val MAX_RENAME_ATTEMPTS = 20
		const val AZIMUTH_MIN = 0f
		const val AZIMUTH_MAX = 360f
		const val AZIMUTH_INVALID = -1f
		@JvmField val FILENAME_FORMATTER = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss")
		@JvmStatic fun getActiveTrackId(cr: ContentResolver): Long {
			var currentTrackId = -1L
			val ca = cr.query(TrackContentProvider.CONTENT_URI_TRACK_ACTIVE, null, null, null, null)
			if (ca != null && ca.moveToFirst()) currentTrackId = ca.getLong(ca.getColumnIndex(TrackContentProvider.Schema.COL_ID))
			ca?.close()
			return currentTrackId
		}
		@JvmStatic fun setTrackName(trackId: Long, name: String?, cr: ContentResolver) {
			val trackUri = ContentUris.withAppendedId(TrackContentProvider.CONTENT_URI_TRACK, trackId)
			val values = ContentValues()
			values.put(TrackContentProvider.Schema.COL_NAME, name)
			cr.update(trackUri, values, null, null)
		}
		@JvmStatic fun setTrackExportDate(trackId: Long, exportTime: Long, cr: ContentResolver) {
			val trackUri = ContentUris.withAppendedId(TrackContentProvider.CONTENT_URI_TRACK, trackId)
			val values = ContentValues()
			values.put(TrackContentProvider.Schema.COL_EXPORT_DATE, exportTime)
			cr.update(trackUri, values, null, null)
		}
        // OSM 업로드 일자 설정은 더 이상 사용하지 않음
		@JvmStatic fun getTrackDirFromDB(cr: ContentResolver, trackId: Long): File? {
			var trackDir: File? = null
			var c = cr.query(ContentUris.withAppendedId(TrackContentProvider.CONTENT_URI_TRACK, trackId), null, null, null, null)
			if (c != null && c.count != 0) {
				c.moveToFirst()
				val trackPath = c.getString(c.getColumnIndex(TrackContentProvider.Schema.COL_DIR))
				if (trackPath != null) trackDir = File(trackPath)
			}
			if (c != null && !c.isClosed) { c.close(); c = null }
			return trackDir
		}
		@JvmStatic fun getTrackDirectory(trackId: Long, context: Context): File {
			val trackStorageDirectory = context.getExternalFilesDir(null).toString() + OSMTracker.Preferences.VAL_STORAGE_DIR + File.separator + "track" + trackId
			return File(trackStorageDirectory)
		}
		@JvmStatic fun getTrackNameInDB(trackId: Long, contentResolver: ContentResolver): String {
			var trackName = ""
			val trackUri = ContentUris.withAppendedId(TrackContentProvider.CONTENT_URI_TRACK, trackId)
			val cursor = contentResolver.query(trackUri, null, null, null, null)
			if (cursor != null && cursor.moveToFirst()) {
				trackName = cursor.getString(cursor.getColumnIndex(TrackContentProvider.Schema.COL_NAME))
				cursor.close()
			}
			return trackName
		}
	}

	private val contentResolver: ContentResolver by lazy { context.contentResolver }

	fun track(trackId: Long, location: Location, azimuth: Float, accuracy: Int, pressure: Float) {
		Log.v(TAG, "Tracking (trackId=$trackId) location: $location azimuth: $azimuth, accuracy: $accuracy")
		val values = ContentValues()
		values.put(TrackContentProvider.Schema.COL_TRACK_ID, trackId)
		values.put(TrackContentProvider.Schema.COL_LATITUDE, location.latitude)
		values.put(TrackContentProvider.Schema.COL_LONGITUDE, location.longitude)
		if (location.hasAltitude()) values.put(TrackContentProvider.Schema.COL_ELEVATION, location.altitude)
		if (location.hasAccuracy()) values.put(TrackContentProvider.Schema.COL_ACCURACY, location.accuracy)
		if (location.hasSpeed()) values.put(TrackContentProvider.Schema.COL_SPEED, location.speed)
		val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
		if (prefs.getBoolean(OSMTracker.Preferences.KEY_GPS_IGNORE_CLOCK, OSMTracker.Preferences.VAL_GPS_IGNORE_CLOCK)) {
			values.put(TrackContentProvider.Schema.COL_TIMESTAMP, System.currentTimeMillis())
		} else {
			values.put(TrackContentProvider.Schema.COL_TIMESTAMP, location.time)
		}
		if (azimuth >= AZIMUTH_MIN && azimuth < AZIMUTH_MAX) {
			values.put(TrackContentProvider.Schema.COL_COMPASS, azimuth)
			values.put(TrackContentProvider.Schema.COL_COMPASS_ACCURACY, accuracy)
		}
		if (pressure != 0f) values.put(TrackContentProvider.Schema.COL_ATMOSPHERIC_PRESSURE, pressure)
		val trackUri = ContentUris.withAppendedId(TrackContentProvider.CONTENT_URI_TRACK, trackId)
		contentResolver.insert(Uri.withAppendedPath(trackUri, TrackContentProvider.Schema.TBL_TRACKPOINT + "s"), values)
	}

	fun wayPoint(trackId: Long, location: Location?, name: String, link: String?, uuid: String?, azimuth: Float, accuracy: Int, pressure: Float) {
		Log.d(TAG, "Tracking waypoint '$name', track=$trackId, uuid=$uuid, nbSatellites=" + (location?.extras?.getInt("satellites") ?: 0) + ", link='" + link + "', location=" + location + ", azimuth=" + azimuth + ", accuracy=" + accuracy)
		if (location != null) {
			val values = ContentValues()
			values.put(TrackContentProvider.Schema.COL_TRACK_ID, trackId)
			values.put(TrackContentProvider.Schema.COL_LATITUDE, location.latitude)
			values.put(TrackContentProvider.Schema.COL_LONGITUDE, location.longitude)
			values.put(TrackContentProvider.Schema.COL_NAME, name)
			values.put(TrackContentProvider.Schema.COL_NBSATELLITES, location.extras?.getInt("satellites") ?: 0)
			if (uuid != null) values.put(TrackContentProvider.Schema.COL_UUID, uuid)
			if (location.hasAltitude()) values.put(TrackContentProvider.Schema.COL_ELEVATION, location.altitude)
			if (location.hasAccuracy()) values.put(TrackContentProvider.Schema.COL_ACCURACY, location.accuracy)
			if (link != null) values.put(TrackContentProvider.Schema.COL_LINK, renameFile(trackId, link, FILENAME_FORMATTER.format(location.time)))
			val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
			if (prefs.getBoolean(OSMTracker.Preferences.KEY_GPS_IGNORE_CLOCK, OSMTracker.Preferences.VAL_GPS_IGNORE_CLOCK)) {
				values.put(TrackContentProvider.Schema.COL_TIMESTAMP, System.currentTimeMillis())
			} else {
				values.put(TrackContentProvider.Schema.COL_TIMESTAMP, location.time)
			}
			if (azimuth >= AZIMUTH_MIN && azimuth < AZIMUTH_MAX) {
				values.put(TrackContentProvider.Schema.COL_COMPASS, azimuth)
				values.put(TrackContentProvider.Schema.COL_COMPASS_ACCURACY, accuracy)
			}
			if (pressure != 0f) values.put(TrackContentProvider.Schema.COL_ATMOSPHERIC_PRESSURE, pressure)
			val trackUri = ContentUris.withAppendedId(TrackContentProvider.CONTENT_URI_TRACK, trackId)
			contentResolver.insert(Uri.withAppendedPath(trackUri, TrackContentProvider.Schema.TBL_WAYPOINT + "s"), values)
		}
	}

	fun updateWayPoint(trackId: Long, uuid: String?, name: String?, link: String?) {
		Log.v(TAG, "Updating waypoint with uuid '$uuid'. New values: name='$name', link='$link'")
		if (uuid != null) {
			val values = ContentValues()
			if (name != null) values.put(TrackContentProvider.Schema.COL_NAME, name)
			if (link != null) values.put(TrackContentProvider.Schema.COL_LINK, link)
			val trackUri = ContentUris.withAppendedId(TrackContentProvider.CONTENT_URI_TRACK, trackId)
			contentResolver.update(Uri.withAppendedPath(trackUri, TrackContentProvider.Schema.TBL_WAYPOINT + "s"), values, "uuid = ?", arrayOf(uuid))
		}
	}

	fun deleteWayPoint(uuid: String?, filepath: String?) {
		Log.v(TAG, "Deleting waypoint with uuid '$uuid")
		if (uuid != null) contentResolver.delete(Uri.withAppendedPath(TrackContentProvider.CONTENT_URI_WAYPOINT_UUID, uuid), null, null)
		val file = if (filepath != null) File(filepath) else null
		if (file != null && file.exists() && file.delete()) Log.v(TAG, "File deleted: $filepath")
	}

	fun stopTracking(trackId: Long) {
		val trackUri = ContentUris.withAppendedId(TrackContentProvider.CONTENT_URI_TRACK, trackId)
		val values = ContentValues()
		values.put(TrackContentProvider.Schema.COL_ACTIVE, TrackContentProvider.Schema.VAL_TRACK_INACTIVE)
		contentResolver.update(trackUri, values, null, null)
	}

	open fun getTrackByStartDate(startDate: Date): Track? {
		val selection = TrackContentProvider.Schema.COL_START_DATE + " = ?"
		val args = arrayOf(startDate.time.toString())
		val cursor = context.contentResolver.query(TrackContentProvider.CONTENT_URI_TRACK, null, selection, args, null)
		var track: Track? = null
		if (cursor != null && cursor.moveToFirst()) {
			val trackId = cursor.getLong(cursor.getColumnIndex(TrackContentProvider.Schema.COL_ID))
			track = Track.build(trackId, cursor, contentResolver, true)
		}
		return track
	}

	open fun getTrackById(trackId: Long): Track {
		val c = context.contentResolver.query(ContentUris.withAppendedId(TrackContentProvider.CONTENT_URI_TRACK, trackId), null, null, null, null)
		Log.d(TAG, "Count of elements in cursor:" + c!!.count)
		c.moveToFirst()
		val track = Track.build(trackId, c, contentResolver, true)
		c.close()
		return track
	}

	open fun getWayPointIdsOfTrack(trackId: Long): List<Int> {
		val out = mutableListOf<Int>()
		val projection = arrayOf(TrackContentProvider.Schema.COL_ID)
		val cWayPoints = contentResolver.query(TrackContentProvider.waypointsUri(trackId), projection, null, null, TrackContentProvider.Schema.COL_TIMESTAMP + " asc")
		Log.d(TAG, "Count of elements in cursor:" + cWayPoints!!.count)
		cWayPoints.moveToFirst()
		while (!cWayPoints.isAfterLast) {
			out.add(cWayPoints.getInt(cWayPoints.getColumnIndex(TrackContentProvider.Schema.COL_ID)))
			cWayPoints.moveToNext()
		}
		cWayPoints.close()
		Log.d(TAG, "Count of elements in returned list:" + out.size)
		return out
	}

	fun getWayPointById(wayPointId: Int): WayPoint {
		val cWayPoint = contentResolver.query(TrackContentProvider.waypointUri(wayPointId.toLong()), null, null, null, null)
		Log.d(TAG, "Count of elements in cursor (expected 1): " + cWayPoint!!.count)
		cWayPoint.moveToFirst()
		return WayPoint(cWayPoint)
	}

	open fun getTrackPointIdsOfTrack(trackId: Long): List<Int> {
		val out = mutableListOf<Int>()
		val projection = arrayOf(TrackContentProvider.Schema.COL_ID)
		val cTrackPoints = contentResolver.query(TrackContentProvider.trackPointsUri(trackId), projection, null, null, TrackContentProvider.Schema.COL_TIMESTAMP + " asc")
		Log.d(TAG, "Count of elements in cTrackPoints:" + cTrackPoints!!.count)
		cTrackPoints.moveToFirst()
		while (!cTrackPoints.isAfterLast) {
			out.add(cTrackPoints.getInt(cTrackPoints.getColumnIndex(TrackContentProvider.Schema.COL_ID)))
			cTrackPoints.moveToNext()
		}
		cTrackPoints.close()
		Log.d(TAG, "Count of elements in returned list:" + out.size)
		return out
	}

	fun getTrackPointById(trackPointId: Int): TrackPoint {
		val cTrackPoint = contentResolver.query(TrackContentProvider.trackpointUri(trackPointId.toLong()), null, null, null, null)
		Log.d(TAG, "Count of elements in cursor (expected 1): " + cTrackPoint!!.count)
		cTrackPoint.moveToFirst()
		return TrackPoint(cTrackPoint)
	}

	private fun renameFile(trackId: Long, from: String, to: String): String {
		var result = from
		val trackDir = getTrackDirectory(trackId, context)
		val ext = from.substring(from.lastIndexOf(".") + 1, from.length)
		val origin = File(trackDir.toString() + File.separator + from)
		if (origin.exists()) {
			var target = File(trackDir.toString() + File.separator + to + "." + ext)
			for (i in 0 until MAX_RENAME_ATTEMPTS) {
				if (!target.exists()) break
				target = File(trackDir.toString() + File.separator + to + i + "." + ext)
			}
			origin.renameTo(target)
			result = target.name
		}
		return result
	}
}


