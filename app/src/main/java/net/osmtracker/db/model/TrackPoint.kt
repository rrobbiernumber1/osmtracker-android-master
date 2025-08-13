package net.osmtracker.db.model

import android.database.Cursor
import net.osmtracker.db.TrackContentProvider

class TrackPoint : Point {

	var speed: Double? = null

	constructor() : super()

	constructor(c: Cursor) : super(c) {
		if (!c.isNull(c.getColumnIndex(TrackContentProvider.Schema.COL_SPEED))) {
			speed = c.getDouble(c.getColumnIndex(TrackContentProvider.Schema.COL_SPEED))
		}
	}
}


