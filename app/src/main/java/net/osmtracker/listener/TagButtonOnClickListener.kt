package net.osmtracker.listener

import android.content.Intent
import android.view.View
import android.widget.Button
import android.widget.Toast
import net.osmtracker.OSMTracker
import net.osmtracker.R
import net.osmtracker.db.TrackContentProvider
import java.util.UUID

class TagButtonOnClickListener(private val currentTrackId: Long) : View.OnClickListener {
	override fun onClick(view: View) {
		val button = view as Button
		val label = button.text.toString().replace("\n".toRegex(), " ")

		val intent = Intent(OSMTracker.INTENT_TRACK_WP)
		intent.putExtra(TrackContentProvider.Schema.COL_TRACK_ID, currentTrackId)
		intent.putExtra(OSMTracker.INTENT_KEY_NAME, label)
		intent.putExtra(OSMTracker.INTENT_KEY_UUID, UUID.randomUUID().toString())

		val packageName = view.context.packageName
		intent.`package` = packageName

		view.context.sendBroadcast(intent)

		Toast.makeText(
			view.context,
			view.context.resources.getString(R.string.tracklogger_tracked) + " " + label,
			Toast.LENGTH_SHORT
		).show()
	}
}


