package net.osmtracker.listener

import android.app.AlertDialog
import android.database.Cursor
import android.view.View

open class EditWaypointDialogOnClickListener(protected var alert: AlertDialog, private var cursor: Cursor?) : View.OnClickListener {
	override fun onClick(view: View) { }
}


