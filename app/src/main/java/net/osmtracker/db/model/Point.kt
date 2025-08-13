package net.osmtracker.db.model

import android.database.Cursor
import net.osmtracker.db.TrackContentProvider

abstract class Point {

	var id: Int? = null
	var trackId: Int? = null
	var latitude: Double = 0.0
	var longitude: Double = 0.0
	var pointTimestamp: Long = 0L
	var elevation: Double? = null
	var accuracy: Double? = null
	var compassHeading: Double? = null
	var compassAccuracy: Double? = null
	var atmosphericPressure: Double? = null

	constructor()

	constructor(c: Cursor) {
		id = c.getInt(c.getColumnIndex(TrackContentProvider.Schema.COL_ID))
		trackId = c.getInt(c.getColumnIndex(TrackContentProvider.Schema.COL_TRACK_ID))
		latitude = c.getDouble(c.getColumnIndex(TrackContentProvider.Schema.COL_LATITUDE))
		longitude = c.getDouble(c.getColumnIndex(TrackContentProvider.Schema.COL_LONGITUDE))
		pointTimestamp = c.getLong(c.getColumnIndex(TrackContentProvider.Schema.COL_TIMESTAMP))
		if (!c.isNull(c.getColumnIndex(TrackContentProvider.Schema.COL_ELEVATION))) {
			elevation = c.getDouble(c.getColumnIndex(TrackContentProvider.Schema.COL_ELEVATION))
		}
		if (!c.isNull(c.getColumnIndex(TrackContentProvider.Schema.COL_ACCURACY))) {
			accuracy = c.getDouble(c.getColumnIndex(TrackContentProvider.Schema.COL_ACCURACY))
		}
		if (!c.isNull(c.getColumnIndex(TrackContentProvider.Schema.COL_COMPASS))) {
			compassHeading = c.getDouble(c.getColumnIndex(TrackContentProvider.Schema.COL_COMPASS))
		}
		if (!c.isNull(c.getColumnIndex(TrackContentProvider.Schema.COL_COMPASS_ACCURACY))) {
			compassAccuracy = c.getDouble(c.getColumnIndex(TrackContentProvider.Schema.COL_COMPASS_ACCURACY))
		}
		if (!c.isNull(c.getColumnIndex(TrackContentProvider.Schema.COL_ATMOSPHERIC_PRESSURE))) {
			atmosphericPressure = c.getDouble(c.getColumnIndex(TrackContentProvider.Schema.COL_ATMOSPHERIC_PRESSURE))
		}
	}
}


