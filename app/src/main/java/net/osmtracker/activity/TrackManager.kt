package net.osmtracker.activity

import android.Manifest
import android.app.AlertDialog
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.ContextMenu
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import net.osmtracker.OSMTracker
import net.osmtracker.R
import net.osmtracker.db.DataHelper
import net.osmtracker.db.TrackContentProvider
import net.osmtracker.exception.CreateTrackException
import net.osmtracker.gpx.ExportToStorageTask
import net.osmtracker.gpx.ExportToTempFileTask
import net.osmtracker.gpx.ZipHelper
import net.osmtracker.util.FileSystemUtils
import net.osmtracker.view.TextNoteDialog
import java.io.File
import java.util.Date

class TrackManager : AppCompatActivity(), TrackListRVAdapter.TrackListRecyclerViewAdapterListener {

	private val TAG = TrackManager::class.java.simpleName

	private val RC_WRITE_STORAGE_DISPLAY_TRACK = 3
	private val RC_WRITE_PERMISSIONS_EXPORT_ALL = 1
	private val RC_WRITE_PERMISSIONS_EXPORT_ONE = 2
	private val RC_GPS_PERMISSION = 5
	private val RC_WRITE_PERMISSIONS_SHARE = 6

	private val PREV_VISIBLE = "prev_visible"

	private val TRACK_ID_NO_TRACK: Long = -1

	private var currentTrackId: Long = TRACK_ID_NO_TRACK
	private var contextMenuSelectedTrackid: Long = TRACK_ID_NO_TRACK
	private var prevItemVisible = -1
	private var TrackLoggerStartIntent: Intent? = null
	private lateinit var recyclerViewAdapter: TrackListRVAdapter
	private var hasDivider = false

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.trackmanager)
		val myToolbar = findViewById<Toolbar>(R.id.my_toolbar)
		setSupportActionBar(myToolbar)
		if (savedInstanceState != null) {
			prevItemVisible = savedInstanceState.getInt(PREV_VISIBLE, -1)
		}
		val fab = findViewById<FloatingActionButton>(R.id.trackmgr_fab)
		fab.setOnClickListener {
			startNewTrack()
		}
		// Intro 기능 제거됨
		val recyclerView = findViewById<RecyclerView>(R.id.recyclerview)
		recyclerView.layoutManager = LinearLayoutManager(this)
		val dividerItemDecoration = DividerItemDecoration(recyclerView.context, DividerItemDecoration.VERTICAL)
		dividerItemDecoration.setDrawable(ContextCompat.getDrawable(this, R.drawable.divider)!!)
		recyclerView.addItemDecoration(dividerItemDecoration)

		// New: left-bottom FAB to add text waypoint to active track directly
		findViewById<FloatingActionButton>(R.id.trackmgr_fab_text_wp).setOnClickListener {
			currentTrackId = DataHelper.getActiveTrackId(contentResolver)
			if (currentTrackId == TRACK_ID_NO_TRACK) {
				Toast.makeText(this, R.string.trackmgr_empty, Toast.LENGTH_SHORT).show()
				return@setOnClickListener
			}
			openTextNoteDialogFor(currentTrackId)
		}
	 }

	override fun onResume() {
		setRecyclerView()
		val emptyView = findViewById<TextView>(R.id.trackmgr_empty)
		if (recyclerViewAdapter.itemCount == 0) {
			emptyView.visibility = View.VISIBLE
		} else {
			emptyView.visibility = View.INVISIBLE
			currentTrackId = DataHelper.getActiveTrackId(contentResolver)
			if (currentTrackId != TRACK_ID_NO_TRACK) {
				Snackbar.make(findViewById(R.id.trackmgr_fab), resources.getString(R.string.trackmgr_continuetrack_hint).replace("{0}", java.lang.Long.toString(currentTrackId)), Snackbar.LENGTH_LONG).setAction("Action", null).show()
			}
		}
		super.onResume()
	}

	private fun setRecyclerView() {
		val recyclerView = findViewById<RecyclerView>(R.id.recyclerview)
		val layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
		recyclerView.layoutManager = layoutManager
		if (!hasDivider) {
			val did = DividerItemDecoration(recyclerView.context, layoutManager.orientation)
			recyclerView.addItemDecoration(did)
			hasDivider = true
		}
		recyclerView.setHasFixedSize(true)
		val cursor = contentResolver.query(TrackContentProvider.CONTENT_URI_TRACK, null, null, null, TrackContentProvider.Schema.COL_START_DATE + " desc")
		recyclerViewAdapter = TrackListRVAdapter(this, cursor!!, this)
		recyclerView.adapter = recyclerViewAdapter
	}

	override fun onSaveInstanceState(outState: Bundle) {
		super.onSaveInstanceState(outState)
		outState.putInt(PREV_VISIBLE, prevItemVisible)
	}

	override fun onRestoreInstanceState(state: Bundle) {
		super.onRestoreInstanceState(state)
		prevItemVisible = state.getInt(PREV_VISIBLE, -1)
	}

	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		menuInflater.inflate(R.menu.trackmgr_menu, menu)
		return true
	}

	override fun onPrepareOptionsMenu(menu: Menu): Boolean {
		if (currentTrackId != -1L) {
			menu.findItem(R.id.trackmgr_menu_continuetrack).isVisible = true
			menu.findItem(R.id.trackmgr_menu_stopcurrenttrack).isVisible = true
		} else {
			menu.findItem(R.id.trackmgr_menu_continuetrack).isVisible = false
			menu.findItem(R.id.trackmgr_menu_stopcurrenttrack).isVisible = false
		}
		val tracksCount = recyclerViewAdapter.itemCount
		menu.findItem(R.id.trackmgr_menu_deletetracks).isVisible = tracksCount > 0
		menu.findItem(R.id.trackmgr_menu_exportall).isVisible = tracksCount > 0
		return super.onPrepareOptionsMenu(menu)
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		when (item.itemId) {
			R.id.trackmgr_menu_newtrack -> startNewTrack()
			R.id.trackmgr_menu_continuetrack -> {
				// resume tracking for current active track
				val intent = Intent(OSMTracker.INTENT_START_TRACKING)
				intent.putExtra(TrackContentProvider.Schema.COL_TRACK_ID, currentTrackId)
				intent.setPackage(this.packageName)
				sendBroadcast(intent)
			}
			R.id.trackmgr_menu_stopcurrenttrack -> stopActiveTrack()
			R.id.trackmgr_menu_deletetracks -> AlertDialog.Builder(this)
				.setTitle(R.string.trackmgr_contextmenu_delete)
				.setMessage(resources.getString(R.string.trackmgr_deleteall_confirm))
				.setCancelable(true)
				.setIcon(android.R.drawable.ic_dialog_alert)
				.setPositiveButton(R.string.menu_deletetracks) { dialog: DialogInterface, _: Int ->
					deleteAllTracks(); dialog.dismiss()
				}
				.setNegativeButton(android.R.string.cancel) { dialog: DialogInterface, _: Int -> dialog.cancel() }
				.create().show()
			R.id.trackmgr_menu_exportall -> if (!writeExternalStoragePermissionGranted()) {
				Log.e(TAG, "ExportAllWrite - Permission asked")
				ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), RC_WRITE_PERMISSIONS_EXPORT_ALL)
			} else exportTracks(false)
			R.id.trackmgr_menu_settings -> startActivity(Intent(this, Preferences::class.java))
			// About 기능 제거됨
		}
		return super.onOptionsItemSelected(item)
	}

 

	private fun startNewTrack() {
		try {
			currentTrackId = createNewTrack()
			val intent = Intent(OSMTracker.INTENT_START_TRACKING)
			intent.putExtra(TrackContentProvider.Schema.COL_TRACK_ID, currentTrackId)
			intent.setPackage(this.packageName)
			sendBroadcast(intent)
		} catch (cte: CreateTrackException) {
			Toast.makeText(this, resources.getString(R.string.trackmgr_newtrack_error).replace("{0}", cte.message ?: ""), Toast.LENGTH_LONG).show()
		}
	}

	private fun exportTracks(onlyContextMenuSelectedTrack: Boolean) {
		var trackIds: LongArray? = null
		if (onlyContextMenuSelectedTrack) {
			trackIds = LongArray(1)
			trackIds[0] = contextMenuSelectedTrackid
		} else {
			val cursor = contentResolver.query(TrackContentProvider.CONTENT_URI_TRACK, null, null, null, TrackContentProvider.Schema.COL_START_DATE + " desc")
			if (cursor != null && cursor.moveToFirst()) {
				trackIds = LongArray(cursor.count)
				val idCol = cursor.getColumnIndex(TrackContentProvider.Schema.COL_ID)
				var i = 0
				do { trackIds[i++] = cursor.getLong(idCol) } while (cursor.moveToNext())
			}
			cursor?.close()
		}
		object : ExportToStorageTask(this@TrackManager, *trackIds!!) {
			override fun onPostExecute(success: Boolean) {
				if (getExportDialog() != null) getExportDialog()!!.dismiss()
				if (!success) {
					AlertDialog.Builder(this@TrackManager).setTitle(android.R.string.dialog_alert_title)
						.setMessage(this@TrackManager.resources.getString(R.string.trackmgr_export_error).replace("{0}", super.getErrorMsg() ?: ""))
						.setIcon(android.R.drawable.ic_dialog_alert)
						.setNeutralButton(android.R.string.ok) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
						.show()
				} else {
					Snackbar.make(findViewById(R.id.trackmgr_fab), resources.getString(R.string.various_export_finished), Snackbar.LENGTH_LONG).setAction("Action", null).show()
					updateTrackItemsInRecyclerView()
				}
			}
		}.execute()
	}

	override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?, trackId: Long) {
		super.onCreateContextMenu(menu, v, menuInfo)
		menuInflater.inflate(R.menu.trackmgr_contextmenu, menu)
		contextMenuSelectedTrackid = trackId
		menu.setHeaderTitle(resources.getString(R.string.trackmgr_contextmenu_title).replace("{0}", java.lang.Long.toString(contextMenuSelectedTrackid)))
		if (currentTrackId == contextMenuSelectedTrackid) {
			menu.findItem(R.id.trackmgr_contextmenu_stop).isVisible = true
		} else {
			menu.findItem(R.id.trackmgr_contextmenu_stop).isVisible = false
		}
		menu.setHeaderTitle(resources.getString(R.string.trackmgr_contextmenu_title).replace("{0}", java.lang.Long.toString(contextMenuSelectedTrackid)))
		if (currentTrackId == contextMenuSelectedTrackid) {
			menu.removeItem(R.id.trackmgr_contextmenu_delete)
		}
	}

	override fun onContextItemSelected(item: MenuItem): Boolean {
		var i: Intent
		when (item.itemId) {
			R.id.trackmgr_contextmenu_stop -> stopActiveTrack()
			R.id.trackmgr_contextmenu_resume -> {
				if (currentTrackId != contextMenuSelectedTrackid) { setActiveTrack(contextMenuSelectedTrackid) }
				val intent = Intent(OSMTracker.INTENT_START_TRACKING)
				intent.putExtra(TrackContentProvider.Schema.COL_TRACK_ID, contextMenuSelectedTrackid)
				intent.setPackage(this.packageName)
				sendBroadcast(intent)
			}
			R.id.trackmgr_contextmenu_delete -> AlertDialog.Builder(this)
				.setTitle(R.string.trackmgr_contextmenu_delete)
				.setMessage(resources.getString(R.string.trackmgr_delete_confirm).replace("{0}", java.lang.Long.toString(contextMenuSelectedTrackid)))
				.setCancelable(true)
				.setIcon(android.R.drawable.ic_dialog_alert)
				.setPositiveButton(android.R.string.ok) { dialog: DialogInterface, _: Int -> deleteTrack(contextMenuSelectedTrackid); dialog.dismiss() }
				.setNegativeButton(android.R.string.cancel) { dialog: DialogInterface, _: Int -> dialog.cancel() }
				.create().show()
			R.id.trackmgr_contextmenu_export -> if (writeExternalStoragePermissionGranted()) { exportTracks(true) } else {
				Log.e(TAG, "ExportAsGPXWrite - Permission asked")
				ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), RC_WRITE_PERMISSIONS_EXPORT_ONE)
			}
			R.id.trackmgr_contextmenu_share -> if (writeExternalStoragePermissionGranted()) { prepareAndShareTrack(contextMenuSelectedTrackid, this) } else {
				Log.e(TAG, "Share GPX - Permission asked")
				ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), RC_WRITE_PERMISSIONS_SHARE)
			}
			R.id.trackmgr_contextmenu_display -> if (writeExternalStoragePermissionGranted()) { displayTrack(contextMenuSelectedTrackid) } else {
				Log.e(TAG, "DisplayTrackMapWrite - Permission asked")
				ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), RC_WRITE_STORAGE_DISPLAY_TRACK)
			}
			R.id.trackmgr_contextmenu_details -> {
				i = Intent(this, TrackDetail::class.java)
				i.putExtra(TrackContentProvider.Schema.COL_TRACK_ID, contextMenuSelectedTrackid)
				startActivity(i)
			}
		}
		return super.onContextItemSelected(item)
	}

	private fun displayTrack(trackId: Long) {
		Log.e(TAG, "On Display Track")
		val i: Intent
		val useOpenStreetMapBackground = PreferenceManager.getDefaultSharedPreferences(this)
			.getBoolean(OSMTracker.Preferences.KEY_UI_DISPLAYTRACK_OSM, OSMTracker.Preferences.VAL_UI_DISPLAYTRACK_OSM)
		i = if (useOpenStreetMapBackground) Intent(this, DisplayTrackMap::class.java) else Intent(this, DisplayTrack::class.java)
		i.putExtra(TrackContentProvider.Schema.COL_TRACK_ID, trackId)
		startActivity(i)
	}

	private fun writeExternalStoragePermissionGranted(): Boolean {
		return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
			Log.d(TAG, "CHECKING - Write")
			ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
		} else {
			Log.d(TAG, "Write External Storage is granted")
			true
		}
	}

	override fun onClick(trackId: Long) {
		val i: Intent
		i = Intent(this, TrackDetail::class.java)
		i.putExtra(TrackContentProvider.Schema.COL_TRACK_ID, trackId)
		startActivity(i)
	}

	@Throws(CreateTrackException::class)
	private fun createNewTrack(): Long {
		val startDate = Date()
		val values = ContentValues()
		values.put(TrackContentProvider.Schema.COL_NAME, DataHelper.FILENAME_FORMATTER.format(Date()))
		values.put(TrackContentProvider.Schema.COL_START_DATE, startDate.time)
		values.put(TrackContentProvider.Schema.COL_ACTIVE, TrackContentProvider.Schema.VAL_TRACK_ACTIVE)
		val trackUri = contentResolver.insert(TrackContentProvider.CONTENT_URI_TRACK, values)
		val trackId = ContentUris.parseId(trackUri!!)
		setActiveTrack(trackId)
		return trackId
	}

	private fun prepareAndShareTrack(trackId: Long, context: Context) {
		object : ExportToTempFileTask(context, trackId) {
			override fun executionCompleted() {
				val tmp = this.getTmpFile()
				val zipFile = ZipHelper.zipCacheFiles(context, trackId, tmp)
				if (zipFile != null) {
					shareFile(zipFile, context)
				}
			}
			override fun onPostExecute(success: Boolean) {
				if (getExportDialog() != null) getExportDialog()!!.dismiss()
				if (!success) {
					AlertDialog.Builder(context)
						.setTitle(android.R.string.dialog_alert_title)
						.setMessage(context.resources.getString(R.string.trackmgr_prepare_for_share_error).replace("{0}", java.lang.Long.toString(trackId)))
						.setIcon(android.R.drawable.ic_dialog_alert)
						.setNeutralButton(android.R.string.ok) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
						.show()
				} else {
					executionCompleted()
				}
			}
		}.execute()
	}

	private fun shareFile(tmpGPXFile: File, context: Context) {
		val trackUriContent: Uri = FileProvider.getUriForFile(context, DataHelper.FILE_PROVIDER_AUTHORITY, tmpGPXFile)
		val shareIntent = Intent()
		shareIntent.action = Intent.ACTION_SEND
		shareIntent.putExtra(Intent.EXTRA_STREAM, trackUriContent)
		shareIntent.type = DataHelper.MIME_TYPE_GPX
		context.startActivity(Intent.createChooser(shareIntent, context.resources.getText(R.string.trackmgr_contextmenu_share)))
	}

	private fun deleteTrack(id: Long) {
		contentResolver.delete(ContentUris.withAppendedId(TrackContentProvider.CONTENT_URI_TRACK, id), null, null)
		updateTrackItemsInRecyclerView()
		val trackStorageDirectory = DataHelper.getTrackDirectory(id, this)
		if (trackStorageDirectory.exists()) {
			FileSystemUtils.delete(trackStorageDirectory, true)
		}
	}

    private fun updateTrackItemsInRecyclerView() {
        val cursorAdapter = recyclerViewAdapter.getCursorAdapter()
        cursorAdapter.cursor.requery()
        recyclerViewAdapter.notifyDataSetChanged()
    }

	private fun deleteAllTracks() {
		val cursor = contentResolver.query(TrackContentProvider.CONTENT_URI_TRACK, null, null, null, TrackContentProvider.Schema.COL_START_DATE + " asc")
		if (currentTrackId != -1L) { stopActiveTrack() }
		recyclerViewAdapter.getItemId(0)
		if (cursor != null && cursor.moveToFirst()) {
			val id_col = cursor.getColumnIndex(TrackContentProvider.Schema.COL_ID)
			do { deleteTrack(cursor.getLong(id_col)) } while (cursor.moveToNext())
			cursor.close()
		}
	}

	private fun setActiveTrack(trackId: Long) {
		stopActiveTrack()
		val values = ContentValues()
		values.put(TrackContentProvider.Schema.COL_ACTIVE, TrackContentProvider.Schema.VAL_TRACK_ACTIVE)
		contentResolver.update(TrackContentProvider.CONTENT_URI_TRACK, values, TrackContentProvider.Schema.COL_ID + " = ?", arrayOf(java.lang.Long.toString(trackId)))
	}

	private fun stopActiveTrack() {
		if (currentTrackId != TRACK_ID_NO_TRACK) {
			val intent = Intent(OSMTracker.INTENT_STOP_TRACKING)
			intent.setPackage(this.packageName)
			sendBroadcast(intent)
			val dataHelper = DataHelper(this)
			dataHelper.stopTracking(currentTrackId)
			currentTrackId = TRACK_ID_NO_TRACK
			updateTrackItemsInRecyclerView()
		}
	}

	override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
		when (requestCode) {
			RC_WRITE_PERMISSIONS_EXPORT_ALL -> if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) { exportTracks(false) } else {
				Log.w(TAG, "we should explain why we need write permission_EXPORT_ALL")
				Toast.makeText(this, R.string.storage_permission_for_export_GPX, Toast.LENGTH_LONG).show()
			}
			RC_WRITE_PERMISSIONS_EXPORT_ONE -> if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) { exportTracks(true) } else {
				Log.w(TAG, "we should explain why we need write permission_EXPORT_ONE")
				Toast.makeText(this, R.string.storage_permission_for_export_GPX, Toast.LENGTH_LONG).show()
			}
			RC_WRITE_STORAGE_DISPLAY_TRACK -> if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) { Log.e(TAG, "Result - Permission granted"); displayTrack(contextMenuSelectedTrackid) } else {
				Log.w(TAG, "Permission not granted")
				Toast.makeText(this, R.string.storage_permission_for_display_track, Toast.LENGTH_LONG).show()
			}
			RC_WRITE_PERMISSIONS_SHARE -> if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) { Log.e(TAG, "Result - Permission granted"); displayTrack(contextMenuSelectedTrackid); prepareAndShareTrack(contextMenuSelectedTrackid, this) } else {
				Log.w(TAG, "Permission not granted")
				Toast.makeText(this, R.string.storage_permission_for_share_track, Toast.LENGTH_LONG).show()
			}
			// no-op for GPS permission related to launching TrackLogger (removed)
		}
	}

	private fun openTextNoteDialogFor(trackId: Long) {
		val dialog = TextNoteDialog(this, trackId)
		dialog.show()
	}
}


