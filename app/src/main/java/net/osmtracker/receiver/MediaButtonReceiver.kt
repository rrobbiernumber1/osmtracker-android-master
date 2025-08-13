package net.osmtracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import net.osmtracker.activity.TrackLogger

class MediaButtonReceiver : BroadcastReceiver() {
	override fun onReceive(context: Context, intent: Intent) {
		val intent2 = Intent(context, TrackLogger::class.java)
		intent2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		intent2.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
		context.startActivity(intent2.putExtra("mediaButton", java.lang.Boolean.TRUE))
	}
}


