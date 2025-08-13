package net.osmtracker.util

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface

object DialogUtils {

	@JvmStatic
	fun showErrorDialog(ctx: Context, msg: CharSequence) {
		AlertDialog.Builder(ctx)
			.setTitle(android.R.string.dialog_alert_title)
			.setIcon(android.R.drawable.ic_dialog_alert)
			.setMessage(msg)
			.setCancelable(true)
			.setNeutralButton(android.R.string.ok, DialogInterface.OnClickListener { dialog, _ -> dialog.dismiss() })
			.create()
			.show()
	}
}


