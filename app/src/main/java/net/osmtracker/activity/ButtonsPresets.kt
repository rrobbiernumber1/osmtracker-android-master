package net.osmtracker.activity

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.util.Log
import android.view.ContextMenu
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import net.osmtracker.OSMTracker
import net.osmtracker.R
import net.osmtracker.layout.DownloadCustomLayoutTask
import net.osmtracker.util.CustomLayoutsUtils
import net.osmtracker.util.FileSystemUtils
import java.io.File
import java.io.FilenameFilter
import java.util.Hashtable

class ButtonsPresets : Activity() {

	private val TAG = Preferences::class.java.simpleName
	private val RC_WRITE_PERMISSION = 1

	private var checkboxHeld: CheckBox? = null
	private lateinit var listener: CheckBoxChangedListener
	private var selected: CheckBox? = null
	private lateinit var defaultCheckBox: CheckBox
	private lateinit var prefs: SharedPreferences
	private var layoutsFileNames = Hashtable<String, String>()
	private var storageDir: String? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		initializeAttributes()
	}

	override fun onResume() {
		super.onResume()
		if (writeExternalStoragePermissionGranted()) {
			refreshActivity()
		} else {
			if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
				Log.w(TAG, "we should explain why we need read permission")
			} else {
				ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), RC_WRITE_PERMISSION)
			}
		}
	}

	fun refreshActivity() {
		val downloadedLayouts = findViewById<LinearLayout>(R.id.list_layouts)
		val defaultSection = findViewById<LinearLayout>(R.id.buttons_presets)
		layoutsFileNames = Hashtable()
		listLayouts(downloadedLayouts)
		checkCurrentLayout(downloadedLayouts, defaultSection)
	}

	private fun initializeAttributes() {
		title = resources.getString(R.string.prefs_ui_buttons_layout)
		setContentView(R.layout.buttons_presets)
		listener = CheckBoxChangedListener()
		prefs = PreferenceManager.getDefaultSharedPreferences(this)
		layoutsFileNames = Hashtable()
		storageDir = File.separator + OSMTracker.Preferences.VAL_STORAGE_DIR
	}

	private fun listLayouts(rootLayout: LinearLayout) {
		val layoutsDir = File(this.getExternalFilesDir(null), storageDir + File.separator + Preferences.LAYOUTS_SUBDIR + File.separator)
		val AT_START = 0
		val fontSize = 20
		if (layoutsDir.exists() && layoutsDir.canRead()) {
			val layoutFiles = layoutsDir.list(FilenameFilter { dir, filename -> filename.endsWith(Preferences.LAYOUT_FILE_EXTENSION) })
			while (rootLayout.getChildAt(0) is CheckBox) {
				rootLayout.removeViewAt(0)
			}
			for (name in layoutFiles) {
				val newCheckBox = CheckBox(this)
				newCheckBox.textSize = fontSize.toFloat()
				val newName = CustomLayoutsUtils.convertFileName(name)
				layoutsFileNames[newName] = name
				newCheckBox.text = newName
				val layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
				layoutParams.setMargins(60, 0, 0, 0)
				newCheckBox.layoutParams = layoutParams
				newCheckBox.setPadding(10, 20, 10, 20)
				newCheckBox.setOnClickListener(listener)
				registerForContextMenu(newCheckBox)
				rootLayout.addView(newCheckBox, AT_START)
			}
		}
		defaultCheckBox = findViewById(R.id.def_layout)
		defaultCheckBox.setOnClickListener(listener)
		layoutsFileNames[defaultCheckBox.text.toString()] = OSMTracker.Preferences.VAL_UI_BUTTONS_LAYOUT
		if (layoutsFileNames.size > 1) {
			findViewById<TextView>(R.id.btnpre_empty).visibility = View.INVISIBLE
		} else {
			findViewById<TextView>(R.id.btnpre_empty).visibility = View.VISIBLE
		}
	}

	private fun checkCurrentLayout(downloadedLayouts: LinearLayout, defaultSection: LinearLayout) {
		val activeLayoutName = CustomLayoutsUtils.getCurrentLayoutName(applicationContext)
		var defLayout = false
		val defCheck = defaultSection.getChildAt(1)
		if (defCheck is CheckBox) {
			val defCheckCast = defCheck
			val defCheckName = layoutsFileNames[defCheckCast.text]
			if (activeLayoutName == defCheckName) {
				selected = defCheckCast
				defLayout = true
			}
		}
		var found = false
		if (!defLayout) {
			for (i in 0 until downloadedLayouts.childCount) {
				val current = downloadedLayouts.getChildAt(i)
				if (current is CheckBox) {
					val currentCast = current
					val currentName = layoutsFileNames[currentCast.text]
					if (activeLayoutName == currentName) {
						selected = currentCast
						found = true
						break
					}
				}
			}
			if (!found) {
				selected = defCheck as CheckBox
				val targetLayout = layoutsFileNames[selected!!.text]
				prefs.edit().putString(OSMTracker.Preferences.KEY_UI_BUTTONS_LAYOUT, targetLayout).commit()
				refreshActivity()
			}
		}
		selected!!.isChecked = true
	}

	private fun selectLayout(pressed: CheckBox) {
		selected!!.isChecked = false
		pressed.isChecked = true
		selected = pressed
		val targetLayout = layoutsFileNames[pressed.text]
		prefs.edit().putString(OSMTracker.Preferences.KEY_UI_BUTTONS_LAYOUT, targetLayout).commit()
	}

	private inner class CheckBoxChangedListener : View.OnClickListener {
		override fun onClick(view: View) {
			selectLayout(view as CheckBox)
		}
	}

	override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
		super.onCreateContextMenu(menu, v, menuInfo)
		menuInflater.inflate(R.menu.btnprecb_context_menu, menu)
		checkboxHeld = v as CheckBox
	}

	@SuppressLint("StaticFieldLeak")
	override fun onContextItemSelected(item: MenuItem): Boolean {
		val externalFilesDir = this.getExternalFilesDir(null)
		when (item.itemId) {
			R.id.cb_update_and_install -> {
				val layoutName = checkboxHeld!!.text.toString()
				val iso = getIso(layoutsFileNames[checkboxHeld!!.text])
				val info = arrayOf(layoutName, iso)
				val dialog = ProgressDialog(checkboxHeld!!.context)
				dialog.setMessage(resources.getString(R.string.buttons_presets_updating_layout))
				dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER)
				dialog.show()
				object : DownloadCustomLayoutTask(this) {
					override fun onPostExecute(status: Boolean) {
						if (status) {
							selectLayout(checkboxHeld!!)
							refreshActivity()
							Toast.makeText(applicationContext, resources.getString(R.string.buttons_presets_successful_update), Toast.LENGTH_LONG).show()
						} else {
							Toast.makeText(applicationContext, resources.getString(R.string.buttons_presets_unsuccessful_update), Toast.LENGTH_LONG).show()
						}
						dialog.dismiss()
					}
				}.execute(*info)
				checkboxHeld!!.isChecked = false
			}
			R.id.cb_delete -> {
				AlertDialog.Builder(this)
					.setTitle(checkboxHeld!!.text)
					.setMessage(resources.getString(R.string.buttons_presets_delete_message).replace("{0}", checkboxHeld!!.text.toString()))
					.setCancelable(true)
					.setIcon(android.R.drawable.ic_dialog_alert)
					.setPositiveButton(resources.getString(R.string.buttons_presets_delete_positive_confirmation)) { _: DialogInterface?, _: Int ->
						val fileName = layoutsFileNames[checkboxHeld!!.text]
						val rootDir = storageDir + File.separator + Preferences.LAYOUTS_SUBDIR + File.separator
						val fileToDelete = File(externalFilesDir, rootDir + fileName)
						val iconDirName = fileName!!.substring(0, fileName.length - CustomLayoutsUtils.LAYOUT_EXTENSION_ISO.length) + Preferences.ICONS_DIR_SUFFIX
						val iconDirToDelete = File(externalFilesDir, rootDir + iconDirName)
						var successfulDeletion = FileSystemUtils.delete(fileToDelete, false)
						if (iconDirToDelete.exists()) successfulDeletion = successfulDeletion and FileSystemUtils.delete(iconDirToDelete, true)
						val messageToShowId = if (successfulDeletion) R.string.buttons_presets_successful_delete else R.string.buttons_presets_unsuccessful_delete
						val message = resources.getString(messageToShowId)
						Log.println(if (successfulDeletion) Log.INFO else Log.ERROR, "TOAST", message)
						Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
						refreshActivity()
					}
					.setNegativeButton(resources.getString(R.string.menu_cancel)) { dialog: DialogInterface, _: Int -> dialog.cancel() }
					.create().show()
			}
		}
		return super.onContextItemSelected(item)
	}

	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		menuInflater.inflate(R.menu.launch_available_layouts_menu, menu)
		return super.onCreateOptionsMenu(menu)
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		if (item.itemId == R.id.launch_available) {
			startActivity(Intent(this, AvailableLayouts::class.java))
		}
		return super.onOptionsItemSelected(item)
	}

	override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
		when (requestCode) {
			RC_WRITE_PERMISSION -> if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				refreshActivity()
			} else {
				Log.w(TAG, "we should explain why we need read permission")
			}
		}
	}

	private fun writeExternalStoragePermissionGranted(): Boolean {
		return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
			ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
		} else {
			true
		}
	}

	private fun getIso(layoutName: String?): String {
		val tmp = layoutName!!.substring(0, layoutName.length - Preferences.LAYOUT_FILE_EXTENSION.length)
		var iso = ""
		var i = tmp.length - AvailableLayouts.ISO_CHARACTER_LENGTH
		while (i < tmp.length) {
			iso += tmp[i]
			i++
		}
		return iso
	}
}


