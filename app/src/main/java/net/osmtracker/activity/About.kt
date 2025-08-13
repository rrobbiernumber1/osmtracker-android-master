package net.osmtracker.activity

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.app.ProgressDialog
import android.content.DialogInterface
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager.NameNotFoundException
import android.os.Bundle
import android.os.Environment
import android.preference.PreferenceManager
import android.view.View
import android.widget.Button
import android.widget.TextView
import net.osmtracker.OSMTracker
import net.osmtracker.R
import net.osmtracker.db.DatabaseHelper
import net.osmtracker.db.ExportDatabaseTask
import java.io.File

class About : Activity() {

	companion object {
		const val DIALOG_EXPORT_DB = 0
		const val DIALOG_EXPORT_DB_COMPLETED = 1
	}

    var exportDbProgressDialog: ProgressDialog? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.about)
		try {
			val pi: PackageInfo = packageManager.getPackageInfo(packageName, 0)
			(findViewById<View>(R.id.about_version) as TextView).text = pi.versionName
		} catch (nnfe: NameNotFoundException) {
		}
		findViewById<View>(R.id.about_debug_info_button).setOnClickListener { v: View ->
			AlertDialog.Builder(v.context)
				.setTitle(R.string.about_debug_info)
				.setMessage(debugInfo)
				.setCancelable(true)
				.setNeutralButton(android.R.string.ok) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
				.create().show()
		}
		findViewById<View>(R.id.about_export_db_button).setOnClickListener {
			showDialog(DIALOG_EXPORT_DB)
			val dbFile = getDatabasePath(DatabaseHelper.DB_NAME)
			val targetFolder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), PreferenceManager.getDefaultSharedPreferences(this@About).getString(OSMTracker.Preferences.KEY_STORAGE_DIR, OSMTracker.Preferences.VAL_STORAGE_DIR))
			ExportDatabaseTask(this@About, targetFolder).execute(dbFile)
		}
	}

	override fun onCreateDialog(id: Int, args: Bundle?): Dialog? {
		when (id) {
			DIALOG_EXPORT_DB -> {
				exportDbProgressDialog = ProgressDialog(this)
				exportDbProgressDialog!!.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
				exportDbProgressDialog!!.isIndeterminate = false
				exportDbProgressDialog!!.progress = 0
				exportDbProgressDialog!!.max = 100
				exportDbProgressDialog!!.setCancelable(false)
				exportDbProgressDialog!!.setMessage(resources.getString(R.string.about_exporting_db))
				exportDbProgressDialog!!.show()
				return exportDbProgressDialog
			}
			DIALOG_EXPORT_DB_COMPLETED -> {
				AlertDialog.Builder(this)
					.setTitle(R.string.about_export_db)
					.setIcon(android.R.drawable.ic_dialog_info)
					.setMessage(getString(R.string.about_export_db_result, args?.getString("result")))
					.setCancelable(true)
					.setNeutralButton(android.R.string.ok) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
					.create()
					.show()
			}
		}
		return null
	}

    // keep Kotlin-generated getter for the public property

	private val debugInfo: String
		get() {
			val externalStorageDir = this.getExternalFilesDir(null)
			val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
			val exportDirectoryNameInPreferences = preferences.getString(OSMTracker.Preferences.KEY_STORAGE_DIR, OSMTracker.Preferences.VAL_STORAGE_DIR)
			val baseExportDirectory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), exportDirectoryNameInPreferences)
			return "External Storage Directory: '" + externalStorageDir + "'\n" +
					"External Storage State: '" + Environment.getExternalStorageState() + "'\n" +
					"Can write to external storage: " + (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) + "\n" +
					"Export External Public Storage Directory: '" + baseExportDirectory + "'\n"
		}
}


