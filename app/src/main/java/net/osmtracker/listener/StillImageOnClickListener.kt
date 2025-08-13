package net.osmtracker.listener

import android.view.View
import net.osmtracker.activity.TrackLogger

class StillImageOnClickListener(private val activity: TrackLogger) : View.OnClickListener {
	override fun onClick(v: View) {
		activity.requestStillImage()
	}
}


