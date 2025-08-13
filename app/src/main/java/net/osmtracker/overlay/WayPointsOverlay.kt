package net.osmtracker.overlay

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.graphics.Point
import android.graphics.drawable.Drawable
import net.osmtracker.R
import net.osmtracker.db.TrackContentProvider
import org.osmdroid.api.IMapView
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.ItemizedOverlay
import org.osmdroid.views.overlay.OverlayItem

class WayPointsOverlay(
		pDefaultMarker: Drawable,
		pContext: Context,
		private val trackId: Long
) : ItemizedOverlay<OverlayItem>(pDefaultMarker) {

	private val wayPointItems: MutableList<OverlayItem> = ArrayList()
	private val pContentResolver: ContentResolver = pContext.contentResolver

	init {
		refresh()
	}

	constructor(pContext: Context, trackId: Long) : this(
		pContext.resources.getDrawable(R.drawable.star), pContext, trackId
	)

	override fun onSnapToItem(pX: Int, pY: Int, pSnapPoint: Point?, pMapView: IMapView): Boolean {
		return false
	}

	override fun createItem(index: Int): OverlayItem {
		return wayPointItems[index]
	}

	override fun size(): Int {
		return wayPointItems.size
	}

	fun refresh() {
		wayPointItems.clear()
		val c: Cursor? = pContentResolver.query(
			TrackContentProvider.waypointsUri(trackId),
			null, null, null, TrackContentProvider.Schema.COL_TIMESTAMP + " asc"
		)
		if (c != null) {
			c.moveToFirst()
			while (!c.isAfterLast) {
				val i = OverlayItem(
					c.getString(c.getColumnIndex(TrackContentProvider.Schema.COL_NAME)),
					c.getString(c.getColumnIndex(TrackContentProvider.Schema.COL_NAME)),
					GeoPoint(
						c.getDouble(c.getColumnIndex(TrackContentProvider.Schema.COL_LATITUDE)),
						c.getDouble(c.getColumnIndex(TrackContentProvider.Schema.COL_LONGITUDE))
					)
				)
				wayPointItems.add(i)
				c.moveToNext()
			}
			c.close()
		}
		populate()
	}
}


