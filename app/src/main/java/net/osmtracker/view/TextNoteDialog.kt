package net.osmtracker.view

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager.LayoutParams
import android.widget.EditText
import net.osmtracker.OSMTracker
import net.osmtracker.R
import net.osmtracker.db.TrackContentProvider
import java.util.UUID

class TextNoteDialog(private val ctx: Context, private var wayPointTrackId: Long) : AlertDialog(ctx) {

	private var input: EditText = EditText(ctx)
	private var wayPointUuid: String? = null

	init {
		setTitle(R.string.gpsstatus_record_textnote)
		setCancelable(true)
		setView(input)

		setButton(BUTTON_POSITIVE, ctx.resources.getString(android.R.string.ok)) { _: DialogInterface, _: Int ->
			val intent = Intent(OSMTracker.INTENT_UPDATE_WP)
			intent.putExtra(TrackContentProvider.Schema.COL_TRACK_ID, wayPointTrackId)
			intent.putExtra(OSMTracker.INTENT_KEY_NAME, input.text.toString())
			intent.putExtra(OSMTracker.INTENT_KEY_UUID, wayPointUuid)
			intent.setPackage(context.packageName)
			ctx.sendBroadcast(intent)
		}

		setButton(BUTTON_NEGATIVE, ctx.resources.getString(android.R.string.cancel)) { dialog: DialogInterface, _: Int ->
			dialog.cancel()
		}

		setOnCancelListener {
			val intent = Intent(OSMTracker.INTENT_DELETE_WP)
			intent.putExtra(OSMTracker.INTENT_KEY_UUID, wayPointUuid)
			intent.setPackage(context.packageName)
			ctx.sendBroadcast(intent)
		}
	}

	override fun onStart() {
		if (wayPointUuid == null) {
			wayPointUuid = UUID.randomUUID().toString()
			val intent = Intent(OSMTracker.INTENT_TRACK_WP)
			intent.putExtra(TrackContentProvider.Schema.COL_TRACK_ID, wayPointTrackId)
			intent.putExtra(OSMTracker.INTENT_KEY_UUID, wayPointUuid)
			intent.putExtra(OSMTracker.INTENT_KEY_NAME, context.resources.getString(R.string.gpsstatus_record_textnote))
			intent.setPackage(context.packageName)
			ctx.sendBroadcast(intent)
		}
		window?.setSoftInputMode(LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
		super.onStart()
	}

	fun resetValues() {
		wayPointUuid = null
		input.setText("")
	}

	override fun onRestoreInstanceState(savedInstanceState: Bundle) {
		val text = savedInstanceState.getString(KEY_INPUT_TEXT)
		if (text != null) input.setText(text)
		wayPointUuid = savedInstanceState.getString(KEY_WAYPOINT_UUID)
		wayPointTrackId = savedInstanceState.getLong(KEY_WAYPOINT_TRACKID)
		super.onRestoreInstanceState(savedInstanceState)
	}

	override fun onSaveInstanceState(): Bundle {
		val extras = super.onSaveInstanceState()
		extras.putString(KEY_INPUT_TEXT, input.text.toString())
		extras.putLong(KEY_WAYPOINT_TRACKID, wayPointTrackId)
		extras.putString(KEY_WAYPOINT_UUID, wayPointUuid)
		return extras
	}

	companion object {
		private const val KEY_INPUT_TEXT = "INPUT_TEXT"
		private const val KEY_WAYPOINT_UUID = "WAYPOINT_UUID"
		private const val KEY_WAYPOINT_TRACKID = "WAYPOINT_TRACKID"
	}
}


