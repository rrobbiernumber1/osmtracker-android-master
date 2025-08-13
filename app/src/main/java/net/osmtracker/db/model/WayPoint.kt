package net.osmtracker.db.model

import android.database.Cursor
import net.osmtracker.db.TrackContentProvider

class WayPoint : Point {

	var uuid: String? = null
	var numberOfSatellites: Int? = null
	var name: String? = null
	var link: String? = null

	constructor() : super()

	constructor(c: Cursor) : super(c) {
		uuid = c.getString(c.getColumnIndex(TrackContentProvider.Schema.COL_UUID))
		numberOfSatellites = c.getInt(c.getColumnIndex(TrackContentProvider.Schema.COL_NBSATELLITES))
		name = c.getString(c.getColumnIndex(TrackContentProvider.Schema.COL_NAME))
	}
}


