package net.osmtracker.activity

import android.Manifest
import android.app.AlertDialog
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper

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

import net.osmtracker.gpx.ExportToStorageTask
import net.osmtracker.gpx.ExportToTempFileTask
import net.osmtracker.service.gps.GPSLogger
import net.osmtracker.util.FileSystemUtils
import java.io.File
import java.util.Date

class TrackManager : AppCompatActivity(), TrackListRVAdapter.TrackListRecyclerViewAdapterListener {

	private val TAG = TrackManager::class.java.simpleName

	private val RC_WRITE_PERMISSIONS_EXPORT_ONE = 2
	private val RC_GPS_PERMISSION = 5
	private val RC_WRITE_PERMISSIONS_SHARE = 6

	private val TRACK_ID_NO_TRACK: Long = -1

	private var currentTrackId: Long = TRACK_ID_NO_TRACK
	private var contextMenuSelectedTrackid: Long = TRACK_ID_NO_TRACK


	private lateinit var recyclerViewAdapter: TrackListRVAdapter
	private var hasDivider = false
	
	// ContentObserver for real-time updates
	private lateinit var trackContentObserver: ContentObserver

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.trackmanager)
		val myToolbar = findViewById<Toolbar>(R.id.my_toolbar)
		setSupportActionBar(myToolbar)

		val fab = findViewById<FloatingActionButton>(R.id.trackmgr_fab)
		fab.setOnClickListener {
			startNewTrack()
		}
		// Intro 기능 제거됨
		val recyclerView = findViewById<RecyclerView>(R.id.recyclerview)
		recyclerView.layoutManager = LinearLayoutManager(this)

		// Initialize ContentObserver for real-time updates
		trackContentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
			override fun onChange(selfChange: Boolean, uri: Uri?) {
				super.onChange(selfChange, uri)
				// Update UI on main thread
				runOnUiThread {
					updateTrackListAndUI()
				}
			}
		}
	}

	override fun onResume() {
		super.onResume()
		// Register ContentObserver
		contentResolver.registerContentObserver(TrackContentProvider.CONTENT_URI_TRACK, true, trackContentObserver)
		// Update current track ID from database
		currentTrackId = getActiveTrackId()
		updateTrackListAndUI()
	}

	override fun onPause() {
		super.onPause()
		// Unregister ContentObserver
		contentResolver.unregisterContentObserver(trackContentObserver)
	}

	private fun updateTrackListAndUI() {
		setRecyclerView()
		updateEmptyView()
		invalidateOptionsMenu() // Update menu items
	}

	private fun updateEmptyView() {
		val emptyView = findViewById<TextView>(R.id.trackmgr_empty)
		if (recyclerViewAdapter.itemCount == 0) {
			emptyView.visibility = View.VISIBLE
		} else {
			emptyView.visibility = View.INVISIBLE
		}
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



	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		menuInflater.inflate(R.menu.trackmgr_menu, menu)
		return true
	}

	override fun onPrepareOptionsMenu(menu: Menu): Boolean {
		val activeTrackId = getActiveTrackId()
		if (activeTrackId != TRACK_ID_NO_TRACK) {
			menu.findItem(R.id.trackmgr_menu_continuetrack).isVisible = true
			menu.findItem(R.id.trackmgr_menu_stopcurrenttrack).isVisible = true
		} else {
			menu.findItem(R.id.trackmgr_menu_continuetrack).isVisible = false
			menu.findItem(R.id.trackmgr_menu_stopcurrenttrack).isVisible = false
		}
		val tracksCount = recyclerViewAdapter.itemCount
		menu.findItem(R.id.trackmgr_menu_deletetracks).isVisible = tracksCount > 0

		return super.onPrepareOptionsMenu(menu)
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		when (item.itemId) {
			R.id.trackmgr_menu_continuetrack -> {
				// GPS 권한 확인
				if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
					ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), RC_GPS_PERMISSION)
					return true
				}
				
				// GPSLogger Service 명시적 시작
				val serviceIntent = Intent(this, GPSLogger::class.java)
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
					startForegroundService(serviceIntent)
				} else {
					startService(serviceIntent)
				}
				
				Log.d(TAG, "Continuing GPS tracking for track #$currentTrackId")
				
				// Service가 완전히 시작될 때까지 잠시 대기 후 Broadcast 전송
				Handler(Looper.getMainLooper()).postDelayed({
					val intent = Intent(OSMTracker.INTENT_START_TRACKING)
					intent.putExtra(TrackContentProvider.Schema.COL_TRACK_ID, currentTrackId)
					intent.setPackage(this.packageName)
					sendBroadcast(intent)
					Log.d(TAG, "Broadcast INTENT_START_TRACKING sent for continue track #$currentTrackId")
				}, 1000) // 1초 지연
				// Update UI immediately
				updateTrackListAndUI()
			}
			R.id.trackmgr_menu_stopcurrenttrack -> {
				stopActiveTrack()
				// Update UI immediately
				updateTrackListAndUI()
			}
			R.id.trackmgr_menu_deletetracks -> AlertDialog.Builder(this)
				.setTitle(R.string.trackmgr_contextmenu_delete)
				.setMessage(resources.getString(R.string.trackmgr_deleteall_confirm))
				.setCancelable(true)
				.setIcon(android.R.drawable.ic_dialog_alert)
				.setPositiveButton(R.string.menu_deletetracks) { dialog: DialogInterface, _: Int ->
					deleteAllTracks(); dialog.dismiss()
					// Update UI immediately
					updateTrackListAndUI()
				}
				.setNegativeButton(android.R.string.cancel) { dialog: DialogInterface, _: Int -> dialog.cancel() }
				.create().show()

			R.id.trackmgr_menu_settings -> startActivity(Intent(this, Preferences::class.java))
			// About 기능 제거됨
		}
		return super.onOptionsItemSelected(item)
	}

 

	private fun startNewTrack() {
		try {
			// GPS 권한 확인
			if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
				ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), RC_GPS_PERMISSION)
				return
			}
			
			currentTrackId = createNewTrack()
			
			// GPSLogger Service 명시적 시작
			val serviceIntent = Intent(this, GPSLogger::class.java)
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				startForegroundService(serviceIntent)
			} else {
				startService(serviceIntent)
			}
			
			Log.d(TAG, "Starting GPS tracking for track #$currentTrackId")
			
			// Service가 완전히 시작될 때까지 잠시 대기 후 Broadcast 전송
			Handler(Looper.getMainLooper()).postDelayed({
				val intent = Intent(OSMTracker.INTENT_START_TRACKING)
				intent.putExtra(TrackContentProvider.Schema.COL_TRACK_ID, currentTrackId)
				intent.setPackage(this.packageName)
				sendBroadcast(intent)
				Log.d(TAG, "Broadcast INTENT_START_TRACKING sent for track #$currentTrackId")
			}, 1000) // 1초 지연
			
			// Update UI immediately
			updateTrackListAndUI()
		} catch (e: Exception) {
			Log.e(TAG, "Error starting new track", e)
			Toast.makeText(this, resources.getString(R.string.trackmgr_newtrack_error).replace("{0}", e.message ?: ""), Toast.LENGTH_LONG).show()
		}
	}

	private fun exportTracks(trackId: Long) {
		val trackIds = LongArray(1)
		trackIds[0] = trackId
		
		object : ExportToStorageTask(this@TrackManager, *trackIds) {
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
		
		// 현재 활성 트랙 ID를 DB에서 가져오기
		val activeTrackId = getActiveTrackId()
		
		menu.setHeaderTitle(resources.getString(R.string.trackmgr_contextmenu_title).replace("{0}", java.lang.Long.toString(contextMenuSelectedTrackid)))
		
		// Stop 메뉴 항목 표시/숨김 처리
		if (activeTrackId == contextMenuSelectedTrackid) {
			menu.findItem(R.id.trackmgr_contextmenu_stop).isVisible = true
			menu.findItem(R.id.trackmgr_contextmenu_resume).isVisible = false
		} else {
			menu.findItem(R.id.trackmgr_contextmenu_stop).isVisible = false
			menu.findItem(R.id.trackmgr_contextmenu_resume).isVisible = true
		}
		
		// 활성 트랙은 삭제 불가
		if (activeTrackId == contextMenuSelectedTrackid) {
			menu.removeItem(R.id.trackmgr_contextmenu_delete)
		}
	}

	override fun onContextItemSelected(item: MenuItem): Boolean {
		var i: Intent
		when (item.itemId) {
			R.id.trackmgr_contextmenu_stop -> {
				stopActiveTrack()
				// Update UI immediately
				updateTrackListAndUI()
			}
			R.id.trackmgr_contextmenu_resume -> {
				// GPS 권한 확인
				if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
					ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), RC_GPS_PERMISSION)
					return true
				}
				
				if (currentTrackId != contextMenuSelectedTrackid) { setActiveTrack(contextMenuSelectedTrackid) }
				
				// GPSLogger Service 명시적 시작
				val serviceIntent = Intent(this, GPSLogger::class.java)
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
					startForegroundService(serviceIntent)
				} else {
					startService(serviceIntent)
				}
				
				Log.d(TAG, "Resuming GPS tracking for track #$contextMenuSelectedTrackid")
				
				// Service가 완전히 시작될 때까지 잠시 대기 후 Broadcast 전송
				Handler(Looper.getMainLooper()).postDelayed({
					val intent = Intent(OSMTracker.INTENT_START_TRACKING)
					intent.putExtra(TrackContentProvider.Schema.COL_TRACK_ID, contextMenuSelectedTrackid)
					intent.setPackage(this.packageName)
					sendBroadcast(intent)
					Log.d(TAG, "Broadcast INTENT_START_TRACKING sent for resume track #$contextMenuSelectedTrackid")
				}, 1000) // 1초 지연
				// Update UI immediately
				updateTrackListAndUI()
			}
			R.id.trackmgr_contextmenu_delete -> AlertDialog.Builder(this)
				.setTitle(R.string.trackmgr_contextmenu_delete)
				.setMessage(resources.getString(R.string.trackmgr_delete_confirm).replace("{0}", java.lang.Long.toString(contextMenuSelectedTrackid)))
				.setCancelable(true)
				.setIcon(android.R.drawable.ic_dialog_alert)
				.setPositiveButton(android.R.string.ok) { dialog: DialogInterface, _: Int -> 
					deleteTrack(contextMenuSelectedTrackid); dialog.dismiss()
					// Update UI immediately
					updateTrackListAndUI()
				}
				.setNegativeButton(android.R.string.cancel) { dialog: DialogInterface, _: Int -> dialog.cancel() }
				.create().show()
			R.id.trackmgr_contextmenu_export -> if (writeExternalStoragePermissionGranted()) { 
				exportTracks(contextMenuSelectedTrackid)
				// Update UI immediately after export
				updateTrackListAndUI()
			} else {
				Log.e(TAG, "ExportAsGPXWrite - Permission asked")
				ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), RC_WRITE_PERMISSIONS_EXPORT_ONE)
			}
			R.id.trackmgr_contextmenu_share -> if (writeExternalStoragePermissionGranted()) { 
				prepareAndShareTrack(contextMenuSelectedTrackid, this)
				// Update UI immediately after share
				updateTrackListAndUI()
			} else {
				Log.e(TAG, "Share GPX - Permission asked")
				ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), RC_WRITE_PERMISSIONS_SHARE)
			}


		}
		return super.onContextItemSelected(item)
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
		// TrackDetail 기능 제거됨 - 트랙 클릭 시 아무 동작 안함
	}


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
				shareFile(tmp, context)
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
		val trackStorageDirectory = DataHelper.getTrackDirectory(id, this)
		if (trackStorageDirectory.exists()) {
			FileSystemUtils.delete(trackStorageDirectory, true)
		}
	}

    	private fun updateTrackItemsInRecyclerView() {
        // Use the comprehensive update method instead of just cursor requery
        updateTrackListAndUI()
    }
    
    private fun getActiveTrackId(): Long {
        val cursor = contentResolver.query(
            TrackContentProvider.CONTENT_URI_TRACK,
            arrayOf(TrackContentProvider.Schema.COL_ID),
            "${TrackContentProvider.Schema.COL_ACTIVE} = ?",
            arrayOf(TrackContentProvider.Schema.VAL_TRACK_ACTIVE.toString()),
            null
        )
        
        return if (cursor != null && cursor.moveToFirst()) {
            val trackId = cursor.getLong(cursor.getColumnIndex(TrackContentProvider.Schema.COL_ID))
            cursor.close()
            trackId
        } else {
            cursor?.close()
            TRACK_ID_NO_TRACK
        }
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
		currentTrackId = trackId
		// Update UI immediately
		updateTrackListAndUI()
	}

	private fun stopActiveTrack() {
		val activeTrackId = getActiveTrackId()
		if (activeTrackId != TRACK_ID_NO_TRACK) {
			Log.d(TAG, "Stopping active track #$activeTrackId")
			
			try {
				// BroadcastReceiver에 stop 신호 전송
				val intent = Intent(OSMTracker.INTENT_STOP_TRACKING)
				intent.setPackage(this.packageName)
				sendBroadcast(intent)
				
				// DB에서 트랙 비활성화
				val dataHelper = DataHelper(this)
				dataHelper.stopTracking(activeTrackId)
				
				// Service 정지 (안전하게)
				try {
					val serviceIntent = Intent(this, GPSLogger::class.java)
					stopService(serviceIntent)
					Log.d(TAG, "GPSLogger service stop requested")
				} catch (e: Exception) {
					Log.w(TAG, "Error stopping GPSLogger service", e)
				}
				
			} catch (e: Exception) {
				Log.e(TAG, "Error stopping track", e)
			} finally {
				currentTrackId = TRACK_ID_NO_TRACK
			}
		}
	}

	override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
		when (requestCode) {

			RC_WRITE_PERMISSIONS_EXPORT_ONE -> if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) { exportTracks(contextMenuSelectedTrackid) } else {
				Log.w(TAG, "we should explain why we need write permission_EXPORT_ONE")
				Toast.makeText(this, R.string.storage_permission_for_export_GPX, Toast.LENGTH_LONG).show()
			}

			RC_WRITE_PERMISSIONS_SHARE -> if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) { Log.e(TAG, "Result - Permission granted"); prepareAndShareTrack(contextMenuSelectedTrackid, this) } else {
				Log.w(TAG, "Permission not granted")
				Toast.makeText(this, R.string.storage_permission_for_share_track, Toast.LENGTH_LONG).show()
			}
			// no-op for GPS permission related to launching TrackLogger (removed)
		}
	}

}


