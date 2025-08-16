package net.osmtracker.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import net.osmtracker.OSMTracker
import net.osmtracker.db.model.Track
import net.osmtracker.util.FileSystemUtils
import java.io.File
import java.io.FilenameFilter

class DatabaseHelper(private val context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

	companion object {
		private val TAG: String = DatabaseHelper::class.java.simpleName
		@JvmField val DB_NAME: String = OSMTracker::class.java.simpleName
		private const val DB_VERSION: Int = 18
		private val SQL_CREATE_TABLE_TRACKPOINT: String = "" +
				"create table " + TrackContentProvider.Schema.TBL_TRACKPOINT + " (" +
				TrackContentProvider.Schema.COL_ID + " integer primary key autoincrement," +
				TrackContentProvider.Schema.COL_TRACK_ID + " integer not null," +
				TrackContentProvider.Schema.COL_LATITUDE + " double not null," +
				TrackContentProvider.Schema.COL_LONGITUDE + " double not null," +
				TrackContentProvider.Schema.COL_SPEED + " double null," +
				TrackContentProvider.Schema.COL_ELEVATION + " double null," +
				TrackContentProvider.Schema.COL_ACCURACY + " double null," +
				TrackContentProvider.Schema.COL_TIMESTAMP + " long not null" +
				")"
		private val SQL_CREATE_IDX_TRACKPOINT_TRACK: String = "create index if not exists " +
				TrackContentProvider.Schema.TBL_TRACKPOINT +
				"_idx ON " + TrackContentProvider.Schema.TBL_TRACKPOINT + "(" + TrackContentProvider.Schema.COL_TRACK_ID + ")"
		private val SQL_CREATE_TABLE_WAYPOINT: String = "" +
				"create table " + TrackContentProvider.Schema.TBL_WAYPOINT + " (" +
				TrackContentProvider.Schema.COL_ID + " integer primary key autoincrement," +
				TrackContentProvider.Schema.COL_TRACK_ID + " integer not null," +
				TrackContentProvider.Schema.COL_UUID + " text," +
				TrackContentProvider.Schema.COL_LATITUDE + " double not null," +
				TrackContentProvider.Schema.COL_LONGITUDE + " double not null," +
				TrackContentProvider.Schema.COL_ELEVATION + " double null," +
				TrackContentProvider.Schema.COL_ACCURACY + " double null," +
				TrackContentProvider.Schema.COL_TIMESTAMP + " long not null," +
				TrackContentProvider.Schema.COL_NAME + " text," +
				TrackContentProvider.Schema.COL_LINK + " text," +
				TrackContentProvider.Schema.COL_NBSATELLITES + " integer not null" +
				")"
		private val SQL_CREATE_IDX_WAYPOINT_TRACK: String = "create index if not exists " +
				TrackContentProvider.Schema.TBL_WAYPOINT +
				"_idx ON " + TrackContentProvider.Schema.TBL_WAYPOINT + "(" + TrackContentProvider.Schema.COL_TRACK_ID + ")"
		private val SQL_CREATE_TABLE_TRACK: String = "" +
				"create table " + TrackContentProvider.Schema.TBL_TRACK + " (" +
				TrackContentProvider.Schema.COL_ID + " integer primary key autoincrement," +
				TrackContentProvider.Schema.COL_NAME + " text," +
				TrackContentProvider.Schema.COL_DESCRIPTION + " text," +
				TrackContentProvider.Schema.COL_TAGS + " text," +
				TrackContentProvider.Schema.COL_START_DATE + " long not null," +
				TrackContentProvider.Schema.COL_DIR + " text," +
				TrackContentProvider.Schema.COL_ACTIVE + " integer not null default 0," +
				TrackContentProvider.Schema.COL_EXPORT_DATE + " long" +
				")"
	}

	override fun onCreate(db: SQLiteDatabase) {
		db.execSQL("drop table if exists " + TrackContentProvider.Schema.TBL_TRACKPOINT)
		db.execSQL(SQL_CREATE_TABLE_TRACKPOINT)
		db.execSQL(SQL_CREATE_IDX_TRACKPOINT_TRACK)
		db.execSQL("drop table if exists " + TrackContentProvider.Schema.TBL_WAYPOINT)
		db.execSQL(SQL_CREATE_TABLE_WAYPOINT)
		db.execSQL(SQL_CREATE_IDX_WAYPOINT_TRACK)
		db.execSQL("drop table if exists " + TrackContentProvider.Schema.TBL_TRACK)
		db.execSQL(SQL_CREATE_TABLE_TRACK)
	}

	override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
		when (oldVersion) {
			1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 -> {
				onCreate(db)
			}
			12 -> {
				manageNewStoragePath(db)
			}
		}
		if (oldVersion <= 13) {
			db.execSQL("alter table " + TrackContentProvider.Schema.TBL_TRACK + " add column " + TrackContentProvider.Schema.COL_DESCRIPTION + " text")
			db.execSQL("alter table " + TrackContentProvider.Schema.TBL_TRACK + " add column " + TrackContentProvider.Schema.COL_TAGS + " text")
		}
		if (oldVersion <= 14) {
			db.execSQL("alter table " + TrackContentProvider.Schema.TBL_TRACKPOINT + " add column " + TrackContentProvider.Schema.COL_SPEED + " double null")
		}
		if (oldVersion <= 15) {
		}
		if (oldVersion <= 16) {
		}
	}

	private fun manageNewStoragePath(db: SQLiteDatabase) {
		Log.d(TAG, "manageNewStoragePath")
		val gpxFilenameFilter = FilenameFilter { _, filename -> filename.lowercase().endsWith(".gpx") }
		val columns = arrayOf(TrackContentProvider.Schema.COL_ID, TrackContentProvider.Schema.COL_DIR)
		val cursor: Cursor? = db.query(TrackContentProvider.Schema.TBL_TRACK, columns, null, null, null, null, null)
		if (cursor != null && cursor.moveToFirst()) {
			Log.d(TAG, "manageNewStoragePath (found " + cursor.count + " tracks to be processed)")
			do {
				val trackId = cursor.getLong(cursor.getColumnIndex(TrackContentProvider.Schema.COL_ID))
				Log.d(TAG, "manageNewStoragePath (" + trackId + ")")
				val oldDirName = cursor.getString(cursor.getColumnIndex(TrackContentProvider.Schema.COL_DIR))
				val newDir = DataHelper.getTrackDirectory(trackId, context)
				val oldDir = File(oldDirName)
				if (oldDir.exists() && oldDir.canRead()) {
					if (!newDir.exists()) newDir.mkdirs()
					if (newDir.exists() && newDir.canWrite()) {
						Log.d(TAG, "manageNewStoragePath (" + trackId + "): copy directory")
						FileSystemUtils.copyDirectoryContents(newDir, oldDir)
						for (gpxFile in newDir.listFiles(gpxFilenameFilter) ?: emptyArray()) {
							Log.d(TAG, "manageNewStoragePath (" + trackId + "): deleting gpx file [" + gpxFile + "]")
							gpxFile.delete()
						}
					} else {
						Log.e(TAG, "manageNewStoragePath (" + trackId + "): directory [" + newDir + "] is not writable or could not be created")
					}
				}
			} while (cursor.moveToNext())
		}
		cursor?.close()
		val vals = ContentValues()
		vals.putNull(TrackContentProvider.Schema.COL_DIR)
		db.update(TrackContentProvider.Schema.TBL_TRACK, vals, null, null)
	}
}


