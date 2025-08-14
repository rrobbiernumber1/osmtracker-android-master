package net.osmtracker.activity

import android.Manifest
import android.app.AlertDialog
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Button
import android.widget.ListView
import android.widget.SimpleAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import net.osmtracker.OSMTracker
import net.osmtracker.R
import net.osmtracker.db.TrackContentProvider
import net.osmtracker.db.model.Track
import net.osmtracker.gpx.ExportToStorageTask
import net.osmtracker.util.MercatorProjection
import java.sql.Date
import java.text.DateFormat

class TrackDetail : TrackDetailEditor(), AdapterView.OnItemClickListener {

	private val RC_WRITE_PERMISSIONS = 1

	private val ITEM_KEY = "key"
	private val ITEM_VALUE = "value"
	private val WP_COUNT_INDEX = 0

	private var trackHasWaypoints = false
	private lateinit var lv: ListView

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState, R.layout.trackdetail, intent.extras!!.getLong(TrackContentProvider.Schema.COL_TRACK_ID))
		lv = findViewById(R.id.trackdetail_list)
		findViewById<Button>(R.id.trackdetail_btn_ok).setOnClickListener {
			save()
			finish()
		}
		findViewById<Button>(R.id.trackdetail_btn_cancel).setOnClickListener { finish() }
		window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
	}

	override fun onResume() {
		super.onResume()
		val cr: ContentResolver = contentResolver
		val cursor = cr.query(ContentUris.withAppendedId(TrackContentProvider.CONTENT_URI_TRACK, trackId), null, null, null, null)
		if (!cursor!!.moveToFirst()) {
			Toast.makeText(this, "Track ID not found.", Toast.LENGTH_SHORT).show()
			cursor.close()
			finish()
			return
		}
		val t = Track.build(trackId, cursor, cr, true)
		bindTrack(t)
		val from = arrayOf(ITEM_KEY, ITEM_VALUE)
		val to = intArrayOf(R.id.trackdetail_item_key, R.id.trackdetail_item_value)
        val wpCount = t.getWpCount()
		trackHasWaypoints = wpCount > 0
		val data: MutableList<HashMap<String, String>> = ArrayList()
		var map = HashMap<String, String>()
		map[ITEM_KEY] = resources.getString(R.string.trackmgr_waypoints_count)
		map[ITEM_VALUE] = Integer.toString(wpCount)
		data.add(WP_COUNT_INDEX, map)
		map = HashMap()
		map[ITEM_KEY] = resources.getString(R.string.trackmgr_trackpoints_count)
        map[ITEM_VALUE] = Integer.toString(t.getTpCount())
		data.add(map)
		map = HashMap()
		map[ITEM_KEY] = resources.getString(R.string.trackdetail_startdate)
        map[ITEM_VALUE] = t.getStartDateAsString()
		data.add(map)
		map = HashMap()
		map[ITEM_KEY] = resources.getString(R.string.trackdetail_enddate)
        map[ITEM_VALUE] = t.getEndDateAsString()
		data.add(map)
		map = HashMap()
		map[ITEM_KEY] = resources.getString(R.string.trackdetail_startloc)
        map[ITEM_VALUE] = MercatorProjection.formatDegreesAsDMS(t.getStartLat(), true) + "  " + MercatorProjection.formatDegreesAsDMS(t.getStartLong(), false)
		data.add(map)
		map = HashMap()
		map[ITEM_KEY] = resources.getString(R.string.trackdetail_endloc)
        map[ITEM_VALUE] = MercatorProjection.formatDegreesAsDMS(t.getEndLat(), true) + "  " + MercatorProjection.formatDegreesAsDMS(t.getEndLong(), false)
		data.add(map)
        // OSM 업로드 정보 제거됨
		map = HashMap()
		map[ITEM_KEY] = resources.getString(R.string.trackdetail_exportdate)
		if (cursor.isNull(cursor.getColumnIndex(TrackContentProvider.Schema.COL_EXPORT_DATE))) {
			map[ITEM_VALUE] = resources.getString(R.string.trackdetail_export_notyet)
		} else {
			map[ITEM_VALUE] = DateFormat.getDateTimeInstance().format(Date(cursor.getLong(cursor.getColumnIndex(TrackContentProvider.Schema.COL_EXPORT_DATE))))
		}
		data.add(map)
		cursor.close()
        val adapter = TrackDetailSimpleAdapter(data, from, to)
		lv.adapter = adapter
		lv.onItemClickListener = this
	}

	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		val inflater: MenuInflater = menuInflater
		inflater.inflate(R.menu.trackdetail_menu, menu)
		return true
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		when (item.itemId) {
			R.id.trackdetail_menu_save -> {
				save(); finish()
			}
			R.id.trackdetail_menu_cancel -> finish()
			R.id.trackdetail_menu_display -> {
				val useOpenStreetMapBackground = PreferenceManager.getDefaultSharedPreferences(this)
					.getBoolean(OSMTracker.Preferences.KEY_UI_DISPLAYTRACK_OSM, OSMTracker.Preferences.VAL_UI_DISPLAYTRACK_OSM)
				val i = if (useOpenStreetMapBackground) Intent(this, DisplayTrackMap::class.java) else Intent(this, DisplayTrack::class.java)
				i.putExtra(TrackContentProvider.Schema.COL_TRACK_ID, trackId)
				startActivity(i)
			}
            R.id.trackdetail_menu_export -> if (writeExternalStoragePermissionGranted()) { exportTrack() }
		}
		return super.onOptionsItemSelected(item)
	}

	private fun writeExternalStoragePermissionGranted(): Boolean {
		return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			true
		} else if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
			if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
				AlertDialog.Builder(this)
					.setTitle(R.string.permission_required)
					.setMessage(R.string.storage_permission_for_export_GPX)
					.setPositiveButton(R.string.acccept) { _, _ ->
						ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), RC_WRITE_PERMISSIONS)
					}
					.setNegativeButton(R.string.menu_cancel) { dialog, _ -> dialog.dismiss() }
					.show()
			} else {
				ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), RC_WRITE_PERMISSIONS)
			}
			ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
		} else {
			ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
		}
	}

	private fun exportTrack() {
        object : ExportToStorageTask(this@TrackDetail, trackId) {}.execute()
		val adapter = lv.adapter as SimpleAdapter
		val data = adapter.getItem(adapter.count - 1) as MutableMap<String, String>
		data[ITEM_VALUE] = DateFormat.getDateTimeInstance().format(java.util.Date(System.currentTimeMillis()))
		adapter.notifyDataSetChanged()
	}

	override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
		when (requestCode) {
			RC_WRITE_PERMISSIONS -> if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				exportTrack()
			}
		}
	}

	override fun onItemClick(parent: AdapterView<*>?, view: View?, position: Int, rowid: Long) {
		if (position != WP_COUNT_INDEX) return
		val i = Intent(this, WaypointList::class.java)
		i.putExtra(TrackContentProvider.Schema.COL_TRACK_ID, trackId)
		startActivity(i)
	}

	private inner class TrackDetailSimpleAdapter(
		data: List<out Map<String, *>>,
		from: Array<String>,
		to: IntArray
	) : SimpleAdapter(this@TrackDetail, data, R.layout.trackdetail_item, from, to) {
		override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
			val v = super.getView(position, convertView, parent)
			if (v !is ViewGroup) return v
			val wantsUnderline = position == WP_COUNT_INDEX && trackHasWaypoints
			val vi = (v as ViewGroup).findViewById<View>(R.id.trackdetail_item_key)
			if (vi != null && vi is TextView) {
				val flags = vi.paintFlags
				if (wantsUnderline) vi.paintFlags = flags or Paint.UNDERLINE_TEXT_FLAG else vi.paintFlags = flags and Paint.UNDERLINE_TEXT_FLAG.inv()
			}
			return v
		}
	}
}


