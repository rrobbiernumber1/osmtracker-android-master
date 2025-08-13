package net.osmtracker.listener

import android.view.View
import net.osmtracker.activity.TrackLogger

class TextNoteOnClickListener(private val trackLogger: TrackLogger) : View.OnClickListener {
	override fun onClick(v: View) {
		trackLogger.showDialog(TrackLogger.DIALOG_TEXT_NOTE)
	}
}


