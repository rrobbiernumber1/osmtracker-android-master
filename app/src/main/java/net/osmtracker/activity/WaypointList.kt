package net.osmtracker.activity

import android.app.AlertDialog
import android.app.ListActivity
import android.content.DialogInterface
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.CursorAdapter
import android.widget.EditText
import android.widget.ListView
import androidx.core.content.FileProvider
import net.osmtracker.R
import net.osmtracker.db.DataHelper
import net.osmtracker.db.TrackContentProvider
import net.osmtracker.listener.EditWaypointDialogOnClickListener
import java.io.File

class WaypointList : ListActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val listView = listView
		listView.fitsSystemWindows = true
		listView.clipToPadding = false
		listView.setPadding(0, 48, 0, 0)
	}

	override fun onResume() {
		val trackId = intent.extras!!.getLong(TrackContentProvider.Schema.COL_TRACK_ID)
        val cursor = contentResolver.query(
			TrackContentProvider.waypointsUri(trackId), null, null, null,
			TrackContentProvider.Schema.COL_TIMESTAMP + " desc"
		)
		startManagingCursor(cursor)
        listAdapter = net.osmtracker.db.WaypointListAdapter(this@WaypointList, cursor!!)
		super.onResume()
	}

	override fun onPause() {
		val adapter = listAdapter as CursorAdapter
		if (adapter != null) {
			val cursor = adapter.cursor
			stopManagingCursor(cursor)
			cursor.close()
			listAdapter = null
		}
		super.onPause()
	}

	override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
		val cursor = (listAdapter as CursorAdapter).cursor
		val dataHelper = DataHelper(l.context)
		val inflater = layoutInflater
		val editWaypointDialog = inflater.inflate(R.layout.edit_waypoint_dialog, null)
		val editWaypointName = editWaypointDialog.findViewById<EditText>(R.id.edit_waypoint_et_name)
		val buttonPreview = editWaypointDialog.findViewById<Button>(R.id.edit_waypoint_button_preview)
		val buttonUpdate = editWaypointDialog.findViewById<Button>(R.id.edit_waypoint_button_update)
		val buttonDelete = editWaypointDialog.findViewById<Button>(R.id.edit_waypoint_button_delete)
		val buttonCancel = editWaypointDialog.findViewById<Button>(R.id.edit_waypoint_button_cancel)

        val oldName = cursor.getString(cursor.getColumnIndex(TrackContentProvider.Schema.COL_NAME))
		editWaypointName.setText(oldName)
		editWaypointName.setSelection(oldName.length)

        val trackId = cursor.getLong(cursor.getColumnIndex(TrackContentProvider.Schema.COL_TRACK_ID))
        val uuid = cursor.getString(cursor.getColumnIndex(TrackContentProvider.Schema.COL_UUID))
        val link = cursor.getString(cursor.getColumnIndex(TrackContentProvider.Schema.COL_LINK))
        val filePath = if (link != null) DataHelper.getTrackDirectory(trackId, l.context).toString() + "/" + link else ""
		val file = if (filePath != null) File(filePath) else null

        if (file != null && file.exists()) {
			try {
				if (isImageFile(filePath) || isAudioFile(filePath)) {
					buttonPreview.visibility = View.VISIBLE
				}
			} catch (_: Exception) {
			}
		}

		val builder = AlertDialog.Builder(this)
		builder.setCancelable(true)
		val alert = builder.create()

        buttonPreview.setOnClickListener(object : EditWaypointDialogOnClickListener(alert, cursor) {
			override fun onClick(view: View) {
                if (filePath.isNotEmpty()) {
					val file = File(filePath)
					val fileUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
						FileProvider.getUriForFile(applicationContext, DataHelper.FILE_PROVIDER_AUTHORITY, file)
					else Uri.fromFile(file)
					val intent = Intent(Intent.ACTION_VIEW)
					intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    if (isImageFile(filePath)) {
						intent.setDataAndType(fileUri, DataHelper.MIME_TYPE_IMAGE)
					} else if (isAudioFile(filePath)) {
						intent.setDataAndType(fileUri, DataHelper.MIME_TYPE_AUDIO)
					}
					if (intent.resolveActivity(packageManager) != null) {
						startActivity(intent)
					}
				}
				alert.dismiss()
			}
		})

        buttonUpdate.setOnClickListener(object : EditWaypointDialogOnClickListener(alert, cursor) {
			override fun onClick(view: View) {
				val newName = editWaypointName.text.toString()
                dataHelper.updateWayPoint(trackId, uuid, newName, link)
				alert.dismiss()
			}
		})

        buttonDelete.setOnClickListener(object : EditWaypointDialogOnClickListener(alert, cursor) {
			override fun onClick(view: View) {
				AlertDialog.Builder(this@WaypointList)
					.setTitle(getString(R.string.delete_waypoint_confirm_dialog_title))
					.setMessage(getString(R.string.delete_waypoint_confirm_dialog_msg))
                    .setPositiveButton(getString(R.string.delete_waypoint_confirm_bt_ok)) { dialog: DialogInterface, _: Int ->
                        dataHelper.deleteWayPoint(uuid, if (filePath.isNotEmpty()) filePath else null)
						cursor.requery()
						alert.dismiss()
						dialog.dismiss()
					}
					.setNegativeButton(getString(R.string.delete_waypoint_confirm_bt_cancel)) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
					.show()
			}
		})

		buttonCancel.setOnClickListener(object : EditWaypointDialogOnClickListener(alert, null) {
			override fun onClick(view: View) {
				alert.dismiss()
			}
		})

		alert.setView(editWaypointDialog)
		alert.show()
		super.onListItemClick(l, v, position, id)
	}

	private fun isImageFile(path: String): Boolean {
		return path.endsWith(DataHelper.EXTENSION_JPG)
	}

	private fun isAudioFile(path: String): Boolean {
		return path.endsWith(DataHelper.EXTENSION_3GPP)
	}
}


