package net.osmtracker.activity

import android.app.Activity
import android.content.ContentUris
import android.content.Intent
import android.content.SharedPreferences
import android.database.ContentObserver
import android.database.Cursor
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.preference.PreferenceManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.Menu
import android.view.View
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import net.osmtracker.OSMTracker
import net.osmtracker.R
import net.osmtracker.db.TrackContentProvider
import net.osmtracker.overlay.WayPointsOverlay
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.mylocation.SimpleLocationOverlay
import java.util.*

class DisplayTrackMap : Activity() {

	private val TAG: String = DisplayTrackMap::class.java.simpleName

	private lateinit var osmView: MapView
	private lateinit var osmViewController: IMapController
	private lateinit var myLocationOverlay: SimpleLocationOverlay
	private lateinit var polyline: Polyline
	private lateinit var wayPointsOverlay: WayPointsOverlay
	private lateinit var scaleBarOverlay: ScaleBarOverlay
	private var currentTrackId: Long = 0
	private var centerToGpsPos: Boolean = true
	private var zoomedToTrackAlready: Boolean = false
	private var currentPosition: GeoPoint? = null
	private var lastTrackPointIdProcessed: Int? = null
	private var trackpointContentObserver: ContentObserver? = null
	private var prefs: SharedPreferences? = null

	companion object {
		private const val CURRENT_ZOOM = "currentZoom"
		private const val CURRENT_SCROLL_X = "currentScrollX"
		private const val CURRENT_SCROLL_Y = "currentScrollY"
		private const val CURRENT_CENTER_TO_GPS_POS = "currentCenterToGpsPos"
		private const val CURRENT_ZOOMED_TO_TRACK = "currentZoomedToTrack"
		private const val LAST_ZOOM = "lastZoomLevel"
		private const val DEFAULT_ZOOM = 16
		private const val CENTER_DEFAULT_ZOOM_LEVEL = 18.0
		private const val ANIMATION_DURATION_MS = 1000L
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		prefs = PreferenceManager.getDefaultSharedPreferences(this)
		setContentView(R.layout.displaytrackmap)
		currentTrackId = intent.extras!!.getLong(TrackContentProvider.Schema.COL_TRACK_ID)
		title = title.toString() + ": #" + currentTrackId
		Configuration.getInstance().load(this, prefs)
		osmView = findViewById(R.id.displaytrackmap_osmView)
		osmView.setMultiTouchControls(true)
		osmView.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
		osmView.keepScreenOn = prefs!!.getBoolean(OSMTracker.Preferences.KEY_UI_DISPLAY_KEEP_ON, OSMTracker.Preferences.VAL_UI_DISPLAY_KEEP_ON)
		osmViewController = osmView.controller
		if (savedInstanceState != null) {
			osmViewController.setZoom(savedInstanceState.getInt(CURRENT_ZOOM, DEFAULT_ZOOM))
			osmView.scrollTo(savedInstanceState.getInt(CURRENT_SCROLL_X, 0), savedInstanceState.getInt(CURRENT_SCROLL_Y, 0))
			centerToGpsPos = savedInstanceState.getBoolean(CURRENT_CENTER_TO_GPS_POS, centerToGpsPos)
			zoomedToTrackAlready = savedInstanceState.getBoolean(CURRENT_ZOOMED_TO_TRACK, zoomedToTrackAlready)
		} else {
			val settings = getPreferences(MODE_PRIVATE)
			osmViewController.setZoom(settings.getInt(LAST_ZOOM, DEFAULT_ZOOM))
		}
		selectTileSource()
		setTileDpiScaling()
		createOverlays()
		trackpointContentObserver = object : ContentObserver(Handler()) {
			override fun onChange(selfChange: Boolean) { pathChanged() }
		}
		findViewById<View>(R.id.displaytrackmap_imgZoomIn).setOnClickListener { osmViewController.zoomIn() }
		findViewById<View>(R.id.displaytrackmap_imgZoomOut).setOnClickListener { osmViewController.zoomOut() }
		findViewById<View>(R.id.displaytrackmap_imgZoomCenter).setOnClickListener {
			centerToGpsPos = true
			currentPosition?.let { osmViewController.animateTo(it, CENTER_DEFAULT_ZOOM_LEVEL, ANIMATION_DURATION_MS) }
		}
	}

	fun selectTileSource() {
		val mapTile = prefs!!.getString(OSMTracker.Preferences.KEY_UI_MAP_TILE, OSMTracker.Preferences.VAL_UI_MAP_TILE_MAPNIK)
		Log.e("TileMapName active", mapTile!!)
		osmView.setTileSource(TileSourceFactory.DEFAULT_TILE_SOURCE)
	}

	fun setTileDpiScaling() { osmView.setTilesScaledToDpi(true) }

	override fun onSaveInstanceState(outState: Bundle) {
		outState.putInt(CURRENT_ZOOM, osmView.zoomLevel)
		outState.putInt(CURRENT_SCROLL_X, osmView.scrollX)
		outState.putInt(CURRENT_SCROLL_Y, osmView.scrollY)
		outState.putBoolean(CURRENT_CENTER_TO_GPS_POS, centerToGpsPos)
		outState.putBoolean(CURRENT_ZOOMED_TO_TRACK, zoomedToTrackAlready)
		super.onSaveInstanceState(outState)
	}

	override fun onResume() {
		super.onResume()
		resumeActivity()
	}

	private fun resumeActivity() {
		osmView.keepScreenOn = prefs!!.getBoolean(OSMTracker.Preferences.KEY_UI_DISPLAY_KEEP_ON, OSMTracker.Preferences.VAL_UI_DISPLAY_KEEP_ON)
		contentResolver.registerContentObserver(TrackContentProvider.trackPointsUri(currentTrackId), true, trackpointContentObserver!!)
		lastTrackPointIdProcessed = null
		pathChanged()
		selectTileSource()
		setTileDpiScaling()
		wayPointsOverlay.refresh()
	}

	override fun onPause() {
		contentResolver.unregisterContentObserver(trackpointContentObserver!!)
		polyline.setPoints(ArrayList())
		super.onPause()
	}

	override fun onStop() {
		super.onStop()
		val settings = getPreferences(MODE_PRIVATE)
		val editor = settings.edit()
		editor.putInt(LAST_ZOOM, osmView.zoomLevel)
		editor.apply()
	}

	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		val inflater = menuInflater
		inflater.inflate(R.menu.displaytrackmap_menu, menu)
		return super.onCreateOptionsMenu(menu)
	}

	override fun onPrepareOptionsMenu(menu: Menu): Boolean {
		menu.findItem(R.id.displaytrackmap_menu_center_to_gps).isEnabled = !centerToGpsPos && currentPosition != null
		return super.onPrepareOptionsMenu(menu)
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		when (item.itemId) {
			R.id.displaytrackmap_menu_center_to_gps -> {
				centerToGpsPos = true
				currentPosition?.let { osmViewController.animateTo(it) }
			}
			R.id.displaytrackmap_menu_settings -> startActivity(Intent(this, Preferences::class.java))
		}
		return super.onOptionsItemSelected(item)
	}

	override fun onTouchEvent(event: MotionEvent): Boolean {
		when (event.action) {
			MotionEvent.ACTION_MOVE -> if (currentPosition != null) centerToGpsPos = false
		}
		return super.onTouchEvent(event)
	}

	private fun createOverlays() {
		val metrics = DisplayMetrics()
		this.windowManager.defaultDisplay.getMetrics(metrics)
		polyline = Polyline()
		val paint: Paint = polyline.outlinePaint
		paint.color = Color.BLUE
		paint.strokeWidth = (metrics.densityDpi / 25.4 / 2).toFloat()
		osmView.overlayManager.add(polyline)
		myLocationOverlay = SimpleLocationOverlay(this)
		osmView.overlays.add(myLocationOverlay)
		wayPointsOverlay = WayPointsOverlay(this, currentTrackId)
		osmView.overlays.add(wayPointsOverlay)
		scaleBarOverlay = ScaleBarOverlay(osmView)
		osmView.overlays.add(scaleBarOverlay)
	}

	private fun pathChanged() {
		if (isFinishing) return
		var doInitialBoundsCalc = false
		var minLat = 91.0
		var minLon = 181.0
		var maxLat = -91.0
		var maxLon = -181.0
		if (!zoomedToTrackAlready && lastTrackPointIdProcessed == null) {
			val proj_active = arrayOf(TrackContentProvider.Schema.COL_ACTIVE)
			val cursor = contentResolver.query(ContentUris.withAppendedId(TrackContentProvider.CONTENT_URI_TRACK, currentTrackId), proj_active, null, null, null)
			if (cursor != null && cursor.moveToFirst()) {
				val colIndex = cursor.getColumnIndex(TrackContentProvider.Schema.COL_ACTIVE)
				if (colIndex != -1) doInitialBoundsCalc = cursor.getInt(colIndex) == TrackContentProvider.Schema.VAL_TRACK_INACTIVE
				cursor.close()
			}
		}
		val projection = arrayOf(TrackContentProvider.Schema.COL_LATITUDE, TrackContentProvider.Schema.COL_LONGITUDE, TrackContentProvider.Schema.COL_ID)
		var selection: String? = null
		var selectionArgs: Array<String>? = null
		if (lastTrackPointIdProcessed != null) {
			selection = TrackContentProvider.Schema.COL_ID + " > ?"
			val selectionArgsList: MutableList<String> = ArrayList()
			selectionArgsList.add(lastTrackPointIdProcessed.toString())
			selectionArgs = selectionArgsList.toTypedArray()
		}
		val c: Cursor? = contentResolver.query(TrackContentProvider.trackPointsUri(currentTrackId), projection, selection, selectionArgs, TrackContentProvider.Schema.COL_ID + " asc")
		if (c != null) {
			val numberOfPointsRetrieved = c.count
			if (numberOfPointsRetrieved > 0) {
				c.moveToFirst()
				var lastLat = 0.0
				var lastLon = 0.0
				val primaryKeyColumnIndex = c.getColumnIndex(TrackContentProvider.Schema.COL_ID)
				val latitudeColumnIndex = c.getColumnIndex(TrackContentProvider.Schema.COL_LATITUDE)
				val longitudeColumnIndex = c.getColumnIndex(TrackContentProvider.Schema.COL_LONGITUDE)
				while (!c.isAfterLast) {
					lastLat = c.getDouble(latitudeColumnIndex)
					lastLon = c.getDouble(longitudeColumnIndex)
					lastTrackPointIdProcessed = c.getInt(primaryKeyColumnIndex)
					polyline.addPoint(GeoPoint(lastLat, lastLon))
					if (doInitialBoundsCalc) {
						if (lastLat < minLat) minLat = lastLat
						if (lastLon < minLon) minLon = lastLon
						if (lastLat > maxLat) maxLat = lastLat
						if (lastLon > maxLon) maxLon = lastLon
					}
					c.moveToNext()
				}
				currentPosition = GeoPoint(lastLat, lastLon)
				myLocationOverlay.setLocation(currentPosition)
				if (centerToGpsPos) osmViewController.setCenter(currentPosition)
				osmView.invalidate()
				if (doInitialBoundsCalc && numberOfPointsRetrieved > 1) {
					val north = maxLat
					val east = maxLon
					val south = minLat
					val west = minLon
					osmView.post {
						osmViewController.zoomToSpan((north - south).toInt(), (east - west).toInt())
						osmViewController.setCenter(GeoPoint((north + south) / 2, (east + west) / 2))
						zoomedToTrackAlready = true
					}
				}
			}
			c.close()
		}
	}
}


