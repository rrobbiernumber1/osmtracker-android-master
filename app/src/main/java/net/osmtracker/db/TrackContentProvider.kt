package net.osmtracker.db

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.sqlite.SQLiteQueryBuilder
import android.net.Uri
import android.util.Log
import net.osmtracker.OSMTracker

open class TrackContentProvider : ContentProvider() {

	companion object {
		private val TAG: String = TrackContentProvider::class.java.simpleName
		const val AUTHORITY: String = OSMTracker.PACKAGE_NAME + ".provider"
		@JvmField val CONTENT_URI_TRACK: Uri = Uri.parse("content://" + AUTHORITY + "/" + Schema.TBL_TRACK)
		@JvmField val CONTENT_URI_TRACK_ACTIVE: Uri = Uri.parse("content://" + AUTHORITY + "/" + Schema.TBL_TRACK + "/active")
		@JvmField val CONTENT_URI_WAYPOINT: Uri = Uri.parse("content://" + AUTHORITY + "/" + Schema.TBL_WAYPOINT)
		@JvmField val CONTENT_URI_WAYPOINT_UUID: Uri = Uri.parse("content://" + AUTHORITY + "/" + Schema.TBL_WAYPOINT + "/uuid")
		@JvmField val CONTENT_URI_TRACKPOINT: Uri = Uri.parse("content://" + AUTHORITY + "/" + Schema.TBL_TRACKPOINT)
		private const val TRACK_TABLES: String = Schema.TBL_TRACK + " left join " + Schema.TBL_TRACKPOINT + " on " + Schema.TBL_TRACK + "." + Schema.COL_ID + " = " + Schema.TBL_TRACKPOINT + "." + Schema.COL_TRACK_ID
		private val TRACK_TABLES_PROJECTION: Array<String> = arrayOf(
				Schema.TBL_TRACK + "." + Schema.COL_ID + " as " + Schema.COL_ID,
				Schema.COL_ACTIVE,
				Schema.COL_DIR,
				Schema.COL_EXPORT_DATE,
                
				Schema.TBL_TRACK + "." + Schema.COL_NAME + " as " + Schema.COL_NAME,
				Schema.COL_DESCRIPTION,
				Schema.COL_TAGS,
				Schema.COL_START_DATE,
				"count(" + Schema.TBL_TRACKPOINT + "." + Schema.COL_ID + ") as " + Schema.COL_TRACKPOINT_COUNT,
				"(SELECT count(" + Schema.TBL_WAYPOINT + "." + Schema.COL_TRACK_ID + ") FROM " + Schema.TBL_WAYPOINT + " WHERE " + Schema.TBL_WAYPOINT + "." + Schema.COL_TRACK_ID + " = " + Schema.TBL_TRACK + "." + Schema.COL_ID + ") as " + Schema.COL_WAYPOINT_COUNT
		)
		private const val TRACK_TABLES_GROUP_BY: String = Schema.TBL_TRACK + "." + Schema.COL_ID
		private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
			addURI(AUTHORITY, Schema.TBL_TRACK, Schema.URI_CODE_TRACK)
			addURI(AUTHORITY, Schema.TBL_TRACK + "/active", Schema.URI_CODE_TRACK_ACTIVE)
			addURI(AUTHORITY, Schema.TBL_TRACK + "/#", Schema.URI_CODE_TRACK_ID)
			addURI(AUTHORITY, Schema.TBL_TRACK + "/#/start", Schema.URI_CODE_TRACK_START)
			addURI(AUTHORITY, Schema.TBL_TRACK + "/#/end", Schema.URI_CODE_TRACK_END)
			addURI(AUTHORITY, Schema.TBL_TRACK + "/#/${Schema.TBL_WAYPOINT}s", Schema.URI_CODE_TRACK_WAYPOINTS)
			addURI(AUTHORITY, Schema.TBL_TRACK + "/#/${Schema.TBL_TRACKPOINT}s", Schema.URI_CODE_TRACK_TRACKPOINTS)
			addURI(AUTHORITY, Schema.TBL_WAYPOINT + "/#", Schema.URI_CODE_WAYPOINT_ID)
			addURI(AUTHORITY, Schema.TBL_WAYPOINT + "/uuid/*", Schema.URI_CODE_WAYPOINT_UUID)
			addURI(AUTHORITY, Schema.TBL_TRACKPOINT + "/#", Schema.URI_CODE_TRACKPOINT_ID)
		}
		@JvmStatic fun waypointsUri(trackId: Long): Uri = Uri.withAppendedPath(ContentUris.withAppendedId(CONTENT_URI_TRACK, trackId), Schema.TBL_WAYPOINT + "s")
		@JvmStatic fun waypointUri(waypointId: Long): Uri = ContentUris.withAppendedId(CONTENT_URI_WAYPOINT, waypointId)
		@JvmStatic fun trackPointsUri(trackId: Long): Uri = Uri.withAppendedPath(ContentUris.withAppendedId(CONTENT_URI_TRACK, trackId), Schema.TBL_TRACKPOINT + "s")
		@JvmStatic fun trackpointUri(trackpointId: Long): Uri = ContentUris.withAppendedId(CONTENT_URI_TRACKPOINT, trackpointId)
		@JvmStatic fun trackStartUri(trackId: Long): Uri = Uri.withAppendedPath(ContentUris.withAppendedId(CONTENT_URI_TRACK, trackId), "start")
		@JvmStatic fun trackEndUri(trackId: Long): Uri = Uri.withAppendedPath(ContentUris.withAppendedId(CONTENT_URI_TRACK, trackId), "end")
	}

	protected lateinit var dbHelper: DatabaseHelper

	override fun onCreate(): Boolean {
		dbHelper = DatabaseHelper(context!!)
		return true
	}

	override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
		Log.v(TAG, "delete(), uri=$uri")
		val count = when (uriMatcher.match(uri)) {
			Schema.URI_CODE_TRACK -> dbHelper.writableDatabase.delete(Schema.TBL_TRACK, selection, selectionArgs)
			Schema.URI_CODE_TRACK_ID -> {
				val trackId = ContentUris.parseId(uri).toString()
				dbHelper.writableDatabase.delete(Schema.TBL_WAYPOINT, Schema.COL_TRACK_ID + " = ?", arrayOf(trackId))
				dbHelper.writableDatabase.delete(Schema.TBL_TRACKPOINT, Schema.COL_TRACK_ID + " = ?", arrayOf(trackId))
				dbHelper.writableDatabase.delete(Schema.TBL_TRACK, Schema.COL_ID + " = ?", arrayOf(trackId))
			}
			Schema.URI_CODE_WAYPOINT_UUID -> {
				val uuid = uri.lastPathSegment
				if (uuid != null) dbHelper.writableDatabase.delete(Schema.TBL_WAYPOINT, Schema.COL_UUID + " = ?", arrayOf(uuid)) else 0
			}
			else -> throw IllegalArgumentException("Unknown URI: $uri")
		}
		context!!.contentResolver.notifyChange(uri, null)
		return count
	}

	override fun getType(uri: Uri): String {
		Log.v(TAG, "getType(), uri=$uri")
		return when (uriMatcher.match(uri)) {
			Schema.URI_CODE_TRACK_TRACKPOINTS -> ContentResolver.CURSOR_DIR_BASE_TYPE + "/vnd." + OSMTracker.PACKAGE_NAME + "." + Schema.TBL_TRACKPOINT
			Schema.URI_CODE_TRACK_WAYPOINTS -> ContentResolver.CURSOR_DIR_BASE_TYPE + "/vnd." + OSMTracker.PACKAGE_NAME + "." + Schema.TBL_WAYPOINT
			Schema.URI_CODE_TRACK -> ContentResolver.CURSOR_DIR_BASE_TYPE + "/vnd." + OSMTracker.PACKAGE_NAME + "." + Schema.TBL_TRACK
			else -> throw IllegalArgumentException("Unknown URL: $uri")
		}
	}

	// Signatures must match Java ContentProvider (nullable args)
	override fun insert(uri: Uri, values: ContentValues?): Uri? {
		Log.v(TAG, "insert(), uri=$uri, values=$values")
		val v = values ?: return null
		return when (uriMatcher.match(uri)) {
			Schema.URI_CODE_TRACK_TRACKPOINTS -> {
				if (v.containsKey(Schema.COL_TRACK_ID) && v.containsKey(Schema.COL_LONGITUDE) && v.containsKey(Schema.COL_LATITUDE) && v.containsKey(Schema.COL_TIMESTAMP)) {
					val rowId = dbHelper.writableDatabase.insert(Schema.TBL_TRACKPOINT, null, v)
					if (rowId > 0) {
						val trackpointUri = ContentUris.withAppendedId(uri, rowId)
						context!!.contentResolver.notifyChange(trackpointUri, null)
						trackpointUri
					} else null
				} else throw IllegalArgumentException("values should provide ${Schema.COL_LONGITUDE}, ${Schema.COL_LATITUDE}, ${Schema.COL_TIMESTAMP}")
			}
			Schema.URI_CODE_TRACK_WAYPOINTS -> {
				if (v.containsKey(Schema.COL_TRACK_ID) && v.containsKey(Schema.COL_LONGITUDE) && v.containsKey(Schema.COL_LATITUDE) && v.containsKey(Schema.COL_TIMESTAMP)) {
					val rowId = dbHelper.writableDatabase.insert(Schema.TBL_WAYPOINT, null, v)
					if (rowId > 0) {
						val waypointUri = ContentUris.withAppendedId(uri, rowId)
						context!!.contentResolver.notifyChange(waypointUri, null)
						waypointUri
					} else null
				} else throw IllegalArgumentException("values should provide ${Schema.COL_LONGITUDE}, ${Schema.COL_LATITUDE}, ${Schema.COL_TIMESTAMP}")
			}
			Schema.URI_CODE_TRACK -> {
				if (v.containsKey(Schema.COL_START_DATE)) {
					val rowId = dbHelper.writableDatabase.insert(Schema.TBL_TRACK, null, v)
					if (rowId > 0) {
						val trackUri = ContentUris.withAppendedId(CONTENT_URI_TRACK, rowId)
						context!!.contentResolver.notifyChange(trackUri, null)
						trackUri
					} else null
				} else throw IllegalArgumentException("values should provide ${Schema.COL_START_DATE}")
			}
			else -> throw IllegalArgumentException("Unknown URI: $uri")
		}
	}

	override fun query(uri: Uri, projectionIn: Array<String>?, selectionIn: String?, selectionArgsIn: Array<String>?, sortOrderIn: String?): Cursor? {
		Log.v(TAG, "query(), uri=$uri")
		val qb = SQLiteQueryBuilder()
		var projection = projectionIn
		var selection = selectionIn
		var selectionArgs = selectionArgsIn
		var sortOrder = sortOrderIn
		var groupBy: String? = null
		var limit: String? = null
		when (uriMatcher.match(uri)) {
			Schema.URI_CODE_TRACK_TRACKPOINTS -> {
				val trackId = uri.pathSegments[1]
				qb.tables = Schema.TBL_TRACKPOINT
				selection = Schema.COL_TRACK_ID + " = ?"
				if (selectionIn != null) selection += " AND $selectionIn"
				val args = mutableListOf(trackId)
				if (selectionArgsIn != null) args.addAll(selectionArgsIn)
				selectionArgs = args.toTypedArray()
			}
			Schema.URI_CODE_TRACK_WAYPOINTS -> {
				if (selectionIn != null || selectionArgsIn != null) throw UnsupportedOperationException()
				val trackId = uri.pathSegments[1]
				qb.tables = Schema.TBL_WAYPOINT
				selection = Schema.COL_TRACK_ID + " = ?"
				selectionArgs = arrayOf(trackId)
			}
			Schema.URI_CODE_TRACK_START -> {
				if (selectionIn != null || selectionArgsIn != null) throw UnsupportedOperationException()
				val trackId = uri.pathSegments[1]
				qb.tables = Schema.TBL_TRACKPOINT
				selection = Schema.COL_TRACK_ID + " = ?"
				selectionArgs = arrayOf(trackId)
				sortOrder = Schema.COL_ID + " asc"
				limit = "1"
			}
			Schema.URI_CODE_TRACK_END -> {
				if (selectionIn != null || selectionArgsIn != null) throw UnsupportedOperationException()
				val trackId = uri.pathSegments[1]
				qb.tables = Schema.TBL_TRACKPOINT
				selection = Schema.COL_TRACK_ID + " = ?"
				selectionArgs = arrayOf(trackId)
				sortOrder = Schema.COL_ID + " desc"
				limit = "1"
			}
			Schema.URI_CODE_TRACK -> {
				qb.tables = TRACK_TABLES
				if (projection == null) projection = TRACK_TABLES_PROJECTION
				groupBy = TRACK_TABLES_GROUP_BY
			}
			Schema.URI_CODE_TRACK_ID -> {
				if (selectionIn != null || selectionArgsIn != null) throw UnsupportedOperationException()
				val trackId = uri.lastPathSegment!!
				qb.tables = TRACK_TABLES
				if (projection == null) projection = TRACK_TABLES_PROJECTION
				groupBy = TRACK_TABLES_GROUP_BY
				selection = Schema.TBL_TRACK + "." + Schema.COL_ID + " = ?"
				selectionArgs = arrayOf(trackId)
			}
			Schema.URI_CODE_TRACK_ACTIVE -> {
				if (selectionIn != null || selectionArgsIn != null) throw UnsupportedOperationException()
				qb.tables = Schema.TBL_TRACK
				selection = Schema.COL_ACTIVE + " = ?"
				selectionArgs = arrayOf(Integer.toString(Schema.VAL_TRACK_ACTIVE))
			}
			Schema.URI_CODE_WAYPOINT_ID -> {
				if (selectionIn != null || selectionArgsIn != null) throw UnsupportedOperationException()
				val wayPointId = uri.lastPathSegment!!
				qb.tables = Schema.TBL_WAYPOINT
				selection = Schema.TBL_WAYPOINT + "." + Schema.COL_ID + " = ?"
				selectionArgs = arrayOf(wayPointId)
			}
			Schema.URI_CODE_TRACKPOINT_ID -> {
				if (selectionIn != null || selectionArgsIn != null) throw UnsupportedOperationException()
				val trackPointId = uri.lastPathSegment!!
				qb.tables = Schema.TBL_TRACKPOINT
				selection = Schema.TBL_TRACKPOINT + "." + Schema.COL_ID + " = ?"
				selectionArgs = arrayOf(trackPointId)
			}
			else -> throw IllegalArgumentException("Unknown URI: $uri")
		}
		val c = qb.query(dbHelper.readableDatabase, projection, selection, selectionArgs, groupBy, null, sortOrder, limit)
		c.setNotificationUri(context!!.contentResolver, uri)
		return c
	}

	override fun update(uri: Uri, values: ContentValues?, selectionIn: String?, selectionArgsIn: Array<String>?): Int {
		Log.v(TAG, "update(), uri=$uri")
		val v = values
		val (table, selection, selectionArgs) = when (uriMatcher.match(uri)) {
			Schema.URI_CODE_TRACK_WAYPOINTS -> if (selectionIn == null || selectionArgsIn == null) throw IllegalArgumentException() else Triple(Schema.TBL_WAYPOINT, selectionIn, selectionArgsIn)
			Schema.URI_CODE_TRACK_ID -> if (selectionIn != null || selectionArgsIn != null) throw UnsupportedOperationException() else Triple(Schema.TBL_TRACK, Schema.COL_ID + " = ?", arrayOf(uri.lastPathSegment!!))
			Schema.URI_CODE_TRACK_ACTIVE -> if (selectionIn != null || selectionArgsIn != null) throw UnsupportedOperationException() else Triple(Schema.TBL_TRACK, Schema.COL_ACTIVE + " = ?", arrayOf(Integer.toString(Schema.VAL_TRACK_ACTIVE)))
			Schema.URI_CODE_TRACK -> Triple(Schema.TBL_TRACK, selectionIn, selectionArgsIn)
			else -> throw IllegalArgumentException("Unknown URI: $uri")
		}
		val rows = dbHelper.writableDatabase.update(table, v, selection, selectionArgs)
		context!!.contentResolver.notifyChange(uri, null)
		return rows
	}

	class Schema {
		companion object {
			const val TBL_TRACKPOINT = "trackpoint"
			const val TBL_WAYPOINT = "waypoint"
			const val TBL_TRACK = "track"
			const val COL_ID = "_id"
			const val COL_TRACK_ID = "track_id"
			const val COL_UUID = "uuid"
			const val COL_LONGITUDE = "longitude"
			const val COL_LATITUDE = "latitude"
			const val COL_SPEED = "speed"
			const val COL_ELEVATION = "elevation"
			const val COL_ACCURACY = "accuracy"
			const val COL_NBSATELLITES = "nb_satellites"
			const val COL_TIMESTAMP = "point_timestamp"
			const val COL_NAME = "name"
			const val COL_DESCRIPTION = "description"
			const val COL_TAGS = "tags"
			const val COL_LINK = "link"
			const val COL_START_DATE = "start_date"
			@Deprecated("deprecated") const val COL_DIR = "directory"
			const val COL_ACTIVE = "active"
			const val COL_EXPORT_DATE = "export_date"
            // OSM 업로드 일자 컬럼은 더 이상 사용하지 않음 (DB 스키마 호환을 위해 유지)
            const val COL_OSM_UPLOAD_DATE = "osm_upload_date"
			const val COL_COMPASS = "compass_heading"
			const val COL_COMPASS_ACCURACY = "compass_accuracy"
			const val COL_ATMOSPHERIC_PRESSURE = "atmospheric_pressure"
			const val COL_TRACKPOINT_COUNT = "tp_count"
			const val COL_WAYPOINT_COUNT = "wp_count"
			const val URI_CODE_TRACK = 3
			const val URI_CODE_TRACK_ID = 4
			const val URI_CODE_TRACK_WAYPOINTS = 5
			const val URI_CODE_TRACK_TRACKPOINTS = 6
			const val URI_CODE_TRACK_ACTIVE = 7
			const val URI_CODE_WAYPOINT_UUID = 8
			const val URI_CODE_TRACK_START = 9
			const val URI_CODE_TRACK_END = 10
			const val URI_CODE_WAYPOINT_ID = 11
			const val URI_CODE_TRACKPOINT_ID = 12
			const val VAL_TRACK_ACTIVE = 1
			const val VAL_TRACK_INACTIVE = 0
		}
	}
}


