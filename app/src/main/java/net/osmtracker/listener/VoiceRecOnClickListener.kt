package net.osmtracker.listener

import android.view.View
import net.osmtracker.activity.TrackLogger

class VoiceRecOnClickListener(private val trackLogger: TrackLogger) : View.OnClickListener {
	override fun onClick(v: View) {
		trackLogger.showDialog(TrackLogger.DIALOG_VOICE_RECORDING)
	}
}


