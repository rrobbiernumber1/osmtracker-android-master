package net.osmtracker.view

import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Paint.Align
import android.os.Handler
import android.util.Log
import android.widget.TextView
import net.osmtracker.R
import net.osmtracker.db.TrackContentProvider
import net.osmtracker.util.ArrayUtils
import net.osmtracker.util.MercatorProjection
import java.text.DecimalFormat

class DisplayTrackView : TextView {

	companion object {
		private val TAG: String = DisplayTrackView::class.java.simpleName
		private const val PADDING: Int = 5
		private const val SCALE_WIDTH: Int = 50
		private const val SCALE_DELIM_HEIGHT: Int = 10
		private val SCALE_FORMAT: DecimalFormat = DecimalFormat("0")
	}

	private var coords: Array<DoubleArray>? = null
	private var pixels: Array<IntArray>? = null
	private var wayPointsCoords: Array<DoubleArray>? = null
	private var wayPointsPixels: Array<IntArray>? = null
	private var projection: MercatorProjection? = null
	private val trackPaint: Paint = Paint()
	private var compass: Bitmap? = null
	private var marker: Bitmap? = null
	private var wayPointMarker: Bitmap? = null
	private var meterLabel: String? = null
	private var northLabel: String? = null
	private var currentTrackId: Long = 0

	private inner class TrackPointContentObserver(handler: Handler) : ContentObserver(handler) {
		override fun onChange(selfChange: Boolean) {
			if (width > 0 && height > 0) {
				populateCoords()
				projectData(width, height)
				invalidate()
			}
		}
	}

	private lateinit var trackpointContentObserver: TrackPointContentObserver

	constructor(context: Context) : super(context)

	constructor(context: Context, trackId: Long) : super(context) {
		currentTrackId = trackId
		paint.textAlign = Align.CENTER
		trackPaint.color = currentTextColor
		trackPaint.style = Paint.Style.FILL_AND_STROKE
		meterLabel = resources.getString(R.string.various_unit_meters)
		northLabel = resources.getString(R.string.displaytrack_north)
		marker = BitmapFactory.decodeResource(resources, R.drawable.marker)
		compass = BitmapFactory.decodeResource(resources, android.R.drawable.ic_menu_compass)
		wayPointMarker = BitmapFactory.decodeResource(resources, R.drawable.star)
		trackpointContentObserver = TrackPointContentObserver(Handler())
		context.contentResolver.registerContentObserver(
				TrackContentProvider.trackPointsUri(currentTrackId), true, trackpointContentObserver)
	}

	override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
		Log.v(TAG, "onSizeChanged: $w,$h. Old: $oldw,$oldh")
		populateCoords()
		projectData(w, h)
		super.onSizeChanged(w, h, oldw, oldh)
	}

	override fun onDetachedFromWindow() {
		context.contentResolver.unregisterContentObserver(trackpointContentObserver)
		super.onDetachedFromWindow()
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		val pixelsLocal = pixels
		if (pixelsLocal != null && pixelsLocal.isNotEmpty()) {
			var length = pixelsLocal.size
			for (i in 1 until length) {
				canvas.drawLine(
						PADDING + pixelsLocal[i - 1][MercatorProjection.X.toInt()].toFloat(),
						PADDING + pixelsLocal[i - 1][MercatorProjection.Y.toInt()].toFloat(),
						PADDING + pixelsLocal[i][MercatorProjection.X.toInt()].toFloat(),
						PADDING + pixelsLocal[i][MercatorProjection.Y.toInt()].toFloat(), trackPaint)
			}
			val wpp = wayPointsPixels
			if (wpp != null && wpp.isNotEmpty()) {
				val wpLength = wpp.size
				for (i in 0 until wpLength) {
					canvas.drawBitmap(wayPointMarker!!,
							PADDING + wpp[i][MercatorProjection.X.toInt()].toFloat(),
							PADDING + wpp[i][MercatorProjection.Y.toInt()].toFloat(), paint)
				}
			}
			canvas.drawBitmap(marker!!, pixelsLocal[length - 1][MercatorProjection.X.toInt()].toFloat(),
					pixelsLocal[length - 1][MercatorProjection.Y.toInt()].toFloat(), paint)
			drawScale(canvas)
		}
		drawStatic(canvas)
	}

	private fun drawScale(canvas: Canvas) {
		val scale = projection!!.scale
		Log.v(TAG, "Scale is: $scale")
		canvas.drawLine((width - PADDING - SCALE_WIDTH).toFloat(), (PADDING + SCALE_DELIM_HEIGHT / 2).toFloat(),
				(width - PADDING).toFloat(), (PADDING + SCALE_DELIM_HEIGHT / 2).toFloat(), paint)
		canvas.drawLine((width - PADDING - SCALE_WIDTH).toFloat(), PADDING.toFloat(),
				(width - PADDING - SCALE_WIDTH).toFloat(), (PADDING + SCALE_DELIM_HEIGHT).toFloat(), paint)
		canvas.drawLine((width - PADDING).toFloat(), PADDING.toFloat(), (width - PADDING).toFloat(),
				(PADDING + SCALE_DELIM_HEIGHT).toFloat(), paint)
		canvas.drawText(
				SCALE_FORMAT.format(100 * 1000 * scale * SCALE_WIDTH.toDouble()) + meterLabel,
				(width - PADDING - SCALE_WIDTH / 2).toFloat(),
				(PADDING + SCALE_DELIM_HEIGHT + paint.textSize).toFloat(), paint)
	}

	private fun drawStatic(canvas: Canvas) {
		canvas.drawBitmap(compass!!, PADDING.toFloat(), (height - PADDING - compass!!.height).toFloat(), null)
		canvas.drawText(northLabel!!, (PADDING + compass!!.width / 2).toFloat(),
				(height - PADDING - compass!!.height - 5).toFloat(), paint)
	}

	fun populateCoords() {
		var c: Cursor? = context.contentResolver.query(
				TrackContentProvider.trackPointsUri(currentTrackId), null, null, null,
				TrackContentProvider.Schema.COL_TIMESTAMP + " asc")
		coords = Array(c!!.count) { DoubleArray(2) }
		var i = 0
		c.moveToFirst()
		while (!c.isAfterLast) {
			coords!![i][MercatorProjection.LONGITUDE.toInt()] = c.getDouble(c.getColumnIndex(TrackContentProvider.Schema.COL_LONGITUDE))
			coords!![i][MercatorProjection.LATITUDE.toInt()] = c.getDouble(c.getColumnIndex(TrackContentProvider.Schema.COL_LATITUDE))
			i++
			c.moveToNext()
		}
		c.close()
		Log.v(TAG, "Extracted " + coords!!.size + " track points from DB.")
		c = context.contentResolver.query(
				TrackContentProvider.waypointsUri(currentTrackId), null, null, null,
				TrackContentProvider.Schema.COL_TIMESTAMP + " asc")
		wayPointsCoords = Array(c!!.count) { DoubleArray(2) }
		c.moveToFirst()
		i = 0
		while (!c.isAfterLast) {
			wayPointsCoords!![i][MercatorProjection.LONGITUDE.toInt()] = c.getDouble(c.getColumnIndex(TrackContentProvider.Schema.COL_LONGITUDE))
			wayPointsCoords!![i][MercatorProjection.LATITUDE.toInt()] = c.getDouble(c.getColumnIndex(TrackContentProvider.Schema.COL_LATITUDE))
			i++
			c.moveToNext()
		}
		c.close()
		Log.v(TAG, "Extracted " + wayPointsCoords!!.size + " way points from DB.")
	}

	fun projectData(width: Int, height: Int) {
		val coordsLocal = coords
		if (coordsLocal != null && coordsLocal.isNotEmpty()) {
			projection = MercatorProjection(
					ArrayUtils.findMin(coordsLocal, MercatorProjection.LATITUDE.toInt()),
					ArrayUtils.findMin(coordsLocal, MercatorProjection.LONGITUDE.toInt()),
					ArrayUtils.findMax(coordsLocal, MercatorProjection.LATITUDE.toInt()),
					ArrayUtils.findMax(coordsLocal, MercatorProjection.LONGITUDE.toInt()),
					width - PADDING * 2, height - PADDING * 2)
			pixels = Array(coordsLocal.size) { IntArray(2) }
			var length = pixels!!.size
			for (i in 0 until length) {
				pixels!![i] = projection!!.project(coordsLocal[i][MercatorProjection.LONGITUDE.toInt()],
						coordsLocal[i][MercatorProjection.LATITUDE.toInt()])
			}
			val wpc = wayPointsCoords
			if (wpc != null && wpc.isNotEmpty()) {
				wayPointsPixels = Array(wpc.size) { IntArray(2) }
				length = wayPointsPixels!!.size
				for (i in 0 until length) {
					wayPointsPixels!![i] = projection!!.project(wpc[i][MercatorProjection.LONGITUDE.toInt()], wpc[i][MercatorProjection.LATITUDE.toInt()])
				}
			}
		}
	}
}


