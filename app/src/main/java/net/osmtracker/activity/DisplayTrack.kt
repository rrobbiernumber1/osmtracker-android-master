package net.osmtracker.activity

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup.LayoutParams
import net.osmtracker.db.TrackContentProvider
import net.osmtracker.view.DisplayTrackView

class DisplayTrack : Activity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		val trackId = intent.extras!!.getLong(TrackContentProvider.Schema.COL_TRACK_ID)
		val dtv = DisplayTrackView(this, trackId)
		dtv.layoutParams = LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT)
		title = title.toString() + ": #" + intent.extras!!.getLong(TrackContentProvider.Schema.COL_TRACK_ID)
		setContentView(dtv)
	}
}


