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
		@JvmField val DB_NAME: String = "open-gpx-tracker-session"
		private const val DB_VERSION: Int = 20
		
		// GpxSession 테이블 (CDRoot 엔티티 대응)
		private val SQL_CREATE_TABLE_GPX_SESSION: String = "" +
				"create table " + TrackContentProvider.Schema.TBL_GPX_SESSION + " (" +
				TrackContentProvider.Schema.COL_SESSION_ID + " integer primary key autoincrement," +
				TrackContentProvider.Schema.COL_CONTINUED_AFTER_SAVE + " integer not null default 0," +
				TrackContentProvider.Schema.COL_LAST_FILE_NAME + " text," +
				TrackContentProvider.Schema.COL_LAST_TRACK_SEGMENT_ID + " integer not null default 0" +
				")"
		
		// GpxTrackpoint 테이블 (CDTrackpoint 엔티티 대응)
		private val SQL_CREATE_TABLE_TRACKPOINT: String = "" +
				"create table " + TrackContentProvider.Schema.TBL_TRACKPOINT + " (" +
				TrackContentProvider.Schema.COL_TRACKPOINT_ID + " integer primary key autoincrement," +
				TrackContentProvider.Schema.COL_TRACK_ID + " integer not null," +
				TrackContentProvider.Schema.COL_LATITUDE + " double not null," +
				TrackContentProvider.Schema.COL_LONGITUDE + " double not null," +
				TrackContentProvider.Schema.COL_SPEED + " double null," +
				TrackContentProvider.Schema.COL_ELEVATION + " double null," +
				TrackContentProvider.Schema.COL_ACCURACY + " double null," +
				TrackContentProvider.Schema.COL_TIMESTAMP + " long not null," +
				TrackContentProvider.Schema.COL_TRACK_SEGMENT_ID + " integer not null default 0," +
				TrackContentProvider.Schema.COL_SERIALIZED + " blob null" +
				")"
		private val SQL_CREATE_IDX_TRACKPOINT_TRACK: String = "create index if not exists " +
				TrackContentProvider.Schema.TBL_TRACKPOINT +
				"_idx ON " + TrackContentProvider.Schema.TBL_TRACKPOINT + "(" + TrackContentProvider.Schema.COL_TRACK_ID + ")"
		
		// GpxWaypoint 테이블 (CDWaypoint 엔티티 대응)
		private val SQL_CREATE_TABLE_WAYPOINT: String = "" +
				"create table " + TrackContentProvider.Schema.TBL_WAYPOINT + " (" +
				TrackContentProvider.Schema.COL_WAYPOINT_ID + " integer primary key autoincrement," +
				TrackContentProvider.Schema.COL_TRACK_ID + " integer not null," +
				TrackContentProvider.Schema.COL_UUID + " text," +
				TrackContentProvider.Schema.COL_LATITUDE + " double not null," +
				TrackContentProvider.Schema.COL_LONGITUDE + " double not null," +
				TrackContentProvider.Schema.COL_ELEVATION + " double null," +
				TrackContentProvider.Schema.COL_ACCURACY + " double null," +
				TrackContentProvider.Schema.COL_TIMESTAMP + " long not null," +
				TrackContentProvider.Schema.COL_NAME + " text," +
				TrackContentProvider.Schema.COL_DESC + " text," +
				TrackContentProvider.Schema.COL_LINK + " text," +
				TrackContentProvider.Schema.COL_NBSATELLITES + " integer not null," +
				TrackContentProvider.Schema.COL_SERIALIZED + " blob null" +
				")"
		private val SQL_CREATE_IDX_WAYPOINT_TRACK: String = "create index if not exists " +
				TrackContentProvider.Schema.TBL_WAYPOINT +
				"_idx ON " + TrackContentProvider.Schema.TBL_WAYPOINT + "(" + TrackContentProvider.Schema.COL_TRACK_ID + ")"
		
		// Track 테이블 (기존 구조 유지하면서 실시간 통계 추가)
		private val SQL_CREATE_TABLE_TRACK: String = "" +
				"create table " + TrackContentProvider.Schema.TBL_TRACK + " (" +
				TrackContentProvider.Schema.COL_ID + " integer primary key autoincrement," +
				TrackContentProvider.Schema.COL_NAME + " text," +
				TrackContentProvider.Schema.COL_DESCRIPTION + " text," +
				TrackContentProvider.Schema.COL_TAGS + " text," +
				TrackContentProvider.Schema.COL_START_DATE + " long not null," +
				TrackContentProvider.Schema.COL_DIR + " text," +
				TrackContentProvider.Schema.COL_ACTIVE + " integer not null default 0," +
				TrackContentProvider.Schema.COL_EXPORT_DATE + " long," +
				TrackContentProvider.Schema.COL_TOTAL_TIME + " long not null default 0," +
				TrackContentProvider.Schema.COL_MOVING_TIME + " long not null default 0," +
				TrackContentProvider.Schema.COL_ELEVATION_GAIN + " double not null default 0.0," +
				TrackContentProvider.Schema.COL_ACTIVITY_TYPE + " text not null default 'Walking'" +
				")"
	}

	override fun onCreate(db: SQLiteDatabase) {
		// GpxSession 테이블 생성
		db.execSQL("drop table if exists " + TrackContentProvider.Schema.TBL_GPX_SESSION)
		db.execSQL(SQL_CREATE_TABLE_GPX_SESSION)
		
		// Trackpoint 테이블 생성
		db.execSQL("drop table if exists " + TrackContentProvider.Schema.TBL_TRACKPOINT)
		db.execSQL(SQL_CREATE_TABLE_TRACKPOINT)
		db.execSQL(SQL_CREATE_IDX_TRACKPOINT_TRACK)
		
		// Waypoint 테이블 생성
		db.execSQL("drop table if exists " + TrackContentProvider.Schema.TBL_WAYPOINT)
		db.execSQL(SQL_CREATE_TABLE_WAYPOINT)
		db.execSQL(SQL_CREATE_IDX_WAYPOINT_TRACK)
		
		// Track 테이블 생성
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
		if (oldVersion <= 17) {
		}
		if (oldVersion <= 18) {
			// iOS Core Data 구조에 맞춰 새로운 스키마로 마이그레이션
			migrateToCoreDataStructure(db)
		}
		if (oldVersion <= 19) {
			// serialized 컬럼을 nullable로 변경하기 위해 테이블 재생성
			Log.d(TAG, "Migrating to version 20: making serialized columns nullable")
			
			// Trackpoint 테이블 재생성
			db.execSQL("DROP TABLE IF EXISTS " + TrackContentProvider.Schema.TBL_TRACKPOINT + "_old")
			db.execSQL("ALTER TABLE " + TrackContentProvider.Schema.TBL_TRACKPOINT + " RENAME TO " + TrackContentProvider.Schema.TBL_TRACKPOINT + "_old")
			db.execSQL(SQL_CREATE_TABLE_TRACKPOINT)
			db.execSQL(SQL_CREATE_IDX_TRACKPOINT_TRACK)
			
			// 기존 데이터 복사 (serialized 컬럼 제외)
			db.execSQL("""
				INSERT INTO ${TrackContentProvider.Schema.TBL_TRACKPOINT} 
				(${TrackContentProvider.Schema.COL_TRACKPOINT_ID}, ${TrackContentProvider.Schema.COL_TRACK_ID}, 
				 ${TrackContentProvider.Schema.COL_LATITUDE}, ${TrackContentProvider.Schema.COL_LONGITUDE}, 
				 ${TrackContentProvider.Schema.COL_SPEED}, ${TrackContentProvider.Schema.COL_ELEVATION}, 
				 ${TrackContentProvider.Schema.COL_ACCURACY}, ${TrackContentProvider.Schema.COL_TIMESTAMP}, 
				 ${TrackContentProvider.Schema.COL_TRACK_SEGMENT_ID})
				SELECT ${TrackContentProvider.Schema.COL_TRACKPOINT_ID}, ${TrackContentProvider.Schema.COL_TRACK_ID}, 
					   ${TrackContentProvider.Schema.COL_LATITUDE}, ${TrackContentProvider.Schema.COL_LONGITUDE}, 
					   ${TrackContentProvider.Schema.COL_SPEED}, ${TrackContentProvider.Schema.COL_ELEVATION}, 
					   ${TrackContentProvider.Schema.COL_ACCURACY}, ${TrackContentProvider.Schema.COL_TIMESTAMP}, 
					   ${TrackContentProvider.Schema.COL_TRACK_SEGMENT_ID}
				FROM ${TrackContentProvider.Schema.TBL_TRACKPOINT}_old
			""")
			
			// Waypoint 테이블 재생성
			db.execSQL("DROP TABLE IF EXISTS " + TrackContentProvider.Schema.TBL_WAYPOINT + "_old")
			db.execSQL("ALTER TABLE " + TrackContentProvider.Schema.TBL_WAYPOINT + " RENAME TO " + TrackContentProvider.Schema.TBL_WAYPOINT + "_old")
			db.execSQL(SQL_CREATE_TABLE_WAYPOINT)
			db.execSQL(SQL_CREATE_IDX_WAYPOINT_TRACK)
			
			// 기존 데이터 복사 (serialized 컬럼 제외)
			db.execSQL("""
				INSERT INTO ${TrackContentProvider.Schema.TBL_WAYPOINT} 
				(${TrackContentProvider.Schema.COL_WAYPOINT_ID}, ${TrackContentProvider.Schema.COL_TRACK_ID}, 
				 ${TrackContentProvider.Schema.COL_UUID}, ${TrackContentProvider.Schema.COL_LATITUDE}, 
				 ${TrackContentProvider.Schema.COL_LONGITUDE}, ${TrackContentProvider.Schema.COL_ELEVATION}, 
				 ${TrackContentProvider.Schema.COL_ACCURACY}, ${TrackContentProvider.Schema.COL_TIMESTAMP}, 
				 ${TrackContentProvider.Schema.COL_NAME}, ${TrackContentProvider.Schema.COL_DESC}, 
				 ${TrackContentProvider.Schema.COL_LINK}, ${TrackContentProvider.Schema.COL_NBSATELLITES})
				SELECT ${TrackContentProvider.Schema.COL_WAYPOINT_ID}, ${TrackContentProvider.Schema.COL_TRACK_ID}, 
					   ${TrackContentProvider.Schema.COL_UUID}, ${TrackContentProvider.Schema.COL_LATITUDE}, 
					   ${TrackContentProvider.Schema.COL_LONGITUDE}, ${TrackContentProvider.Schema.COL_ELEVATION}, 
					   ${TrackContentProvider.Schema.COL_ACCURACY}, ${TrackContentProvider.Schema.COL_TIMESTAMP}, 
					   ${TrackContentProvider.Schema.COL_NAME}, ${TrackContentProvider.Schema.COL_DESC}, 
					   ${TrackContentProvider.Schema.COL_LINK}, ${TrackContentProvider.Schema.COL_NBSATELLITES}
				FROM ${TrackContentProvider.Schema.TBL_WAYPOINT}_old
			""")
			
			// 기존 테이블 삭제
			db.execSQL("DROP TABLE IF EXISTS " + TrackContentProvider.Schema.TBL_TRACKPOINT + "_old")
			db.execSQL("DROP TABLE IF EXISTS " + TrackContentProvider.Schema.TBL_WAYPOINT + "_old")
		}
	}
	
	private fun migrateToCoreDataStructure(db: SQLiteDatabase) {
		Log.d(TAG, "Migrating to Core Data structure")
		
		// 1. GpxSession 테이블 생성
		db.execSQL(SQL_CREATE_TABLE_GPX_SESSION)
		
		// 2. 기존 trackpoint 테이블을 새 구조로 마이그레이션
		db.execSQL("ALTER TABLE " + TrackContentProvider.Schema.TBL_TRACKPOINT + " RENAME TO " + TrackContentProvider.Schema.TBL_TRACKPOINT + "_old")
		db.execSQL(SQL_CREATE_TABLE_TRACKPOINT)
		db.execSQL(SQL_CREATE_IDX_TRACKPOINT_TRACK)
		
		// 기존 데이터 복사
		db.execSQL("""
			INSERT INTO ${TrackContentProvider.Schema.TBL_TRACKPOINT} 
			(${TrackContentProvider.Schema.COL_TRACKPOINT_ID}, ${TrackContentProvider.Schema.COL_TRACK_ID}, 
			 ${TrackContentProvider.Schema.COL_LATITUDE}, ${TrackContentProvider.Schema.COL_LONGITUDE}, 
			 ${TrackContentProvider.Schema.COL_SPEED}, ${TrackContentProvider.Schema.COL_ELEVATION}, 
			 ${TrackContentProvider.Schema.COL_ACCURACY}, ${TrackContentProvider.Schema.COL_TIMESTAMP}, 
			 ${TrackContentProvider.Schema.COL_TRACK_SEGMENT_ID})
			SELECT ${TrackContentProvider.Schema.COL_ID}, ${TrackContentProvider.Schema.COL_TRACK_ID}, 
				   ${TrackContentProvider.Schema.COL_LATITUDE}, ${TrackContentProvider.Schema.COL_LONGITUDE}, 
				   ${TrackContentProvider.Schema.COL_SPEED}, ${TrackContentProvider.Schema.COL_ELEVATION}, 
				   ${TrackContentProvider.Schema.COL_ACCURACY}, ${TrackContentProvider.Schema.COL_TIMESTAMP}, 0
			FROM ${TrackContentProvider.Schema.TBL_TRACKPOINT}_old
		""")
		
		// 3. 기존 waypoint 테이블을 새 구조로 마이그레이션
		db.execSQL("ALTER TABLE " + TrackContentProvider.Schema.TBL_WAYPOINT + " RENAME TO " + TrackContentProvider.Schema.TBL_WAYPOINT + "_old")
		db.execSQL(SQL_CREATE_TABLE_WAYPOINT)
		db.execSQL(SQL_CREATE_IDX_WAYPOINT_TRACK)
		
		// 기존 데이터 복사
		db.execSQL("""
			INSERT INTO ${TrackContentProvider.Schema.TBL_WAYPOINT} 
			(${TrackContentProvider.Schema.COL_WAYPOINT_ID}, ${TrackContentProvider.Schema.COL_TRACK_ID}, 
			 ${TrackContentProvider.Schema.COL_UUID}, ${TrackContentProvider.Schema.COL_LATITUDE}, 
			 ${TrackContentProvider.Schema.COL_LONGITUDE}, ${TrackContentProvider.Schema.COL_ELEVATION}, 
			 ${TrackContentProvider.Schema.COL_ACCURACY}, ${TrackContentProvider.Schema.COL_TIMESTAMP}, 
			 ${TrackContentProvider.Schema.COL_NAME}, ${TrackContentProvider.Schema.COL_LINK}, 
			 ${TrackContentProvider.Schema.COL_NBSATELLITES})
			SELECT ${TrackContentProvider.Schema.COL_ID}, ${TrackContentProvider.Schema.COL_TRACK_ID}, 
				   ${TrackContentProvider.Schema.COL_UUID}, ${TrackContentProvider.Schema.COL_LATITUDE}, 
				   ${TrackContentProvider.Schema.COL_LONGITUDE}, ${TrackContentProvider.Schema.COL_ELEVATION}, 
				   ${TrackContentProvider.Schema.COL_ACCURACY}, ${TrackContentProvider.Schema.COL_TIMESTAMP}, 
				   ${TrackContentProvider.Schema.COL_NAME}, ${TrackContentProvider.Schema.COL_LINK}, 
				   ${TrackContentProvider.Schema.COL_NBSATELLITES}
			FROM ${TrackContentProvider.Schema.TBL_WAYPOINT}_old
		""")
		
		// 4. Track 테이블에 실시간 통계 컬럼 추가
		db.execSQL("ALTER TABLE " + TrackContentProvider.Schema.TBL_TRACK + " ADD COLUMN " + TrackContentProvider.Schema.COL_TOTAL_TIME + " long not null default 0")
		db.execSQL("ALTER TABLE " + TrackContentProvider.Schema.TBL_TRACK + " ADD COLUMN " + TrackContentProvider.Schema.COL_MOVING_TIME + " long not null default 0")
		db.execSQL("ALTER TABLE " + TrackContentProvider.Schema.TBL_TRACK + " ADD COLUMN " + TrackContentProvider.Schema.COL_ELEVATION_GAIN + " double not null default 0.0")
		db.execSQL("ALTER TABLE " + TrackContentProvider.Schema.TBL_TRACK + " ADD COLUMN " + TrackContentProvider.Schema.COL_ACTIVITY_TYPE + " text not null default 'Walking'")
		
		// 5. 기존 테이블 삭제
		db.execSQL("DROP TABLE IF EXISTS " + TrackContentProvider.Schema.TBL_TRACKPOINT + "_old")
		db.execSQL("DROP TABLE IF EXISTS " + TrackContentProvider.Schema.TBL_WAYPOINT + "_old")
		
		Log.d(TAG, "Migration to Core Data structure completed")
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


