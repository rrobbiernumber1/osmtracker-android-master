package net.osmtracker.view

import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.util.Log
import android.widget.TextView
import net.osmtracker.R
import net.osmtracker.db.TrackContentProvider

/**
 * 단순화된 트랙 표시 뷰
 * iOS와 동일하게 간단한 GPS 트랙 표시만 수행
 */
class DisplayTrackView : TextView {

	companion object {
		private val TAG: String = DisplayTrackView::class.java.simpleName
		private const val PADDING: Int = 20
	}

	private var trackPoints: MutableList<Pair<Double, Double>> = mutableListOf()
	private val trackPaint: Paint = Paint()
	private val trackPath: Path = Path()
	private var currentTrackId: Long = 0
	private val trackpointContentObserver = TrackPointContentObserver(Handler())

	private inner class TrackPointContentObserver(handler: Handler) : ContentObserver(handler) {
		override fun onChange(selfChange: Boolean) {
			if (width > 0 && height > 0) {
				loadTrackPoints()
				drawTrackPath()
				invalidate()
			}
		}
	}

	constructor(context: Context, trackId: Long) : super(context) {
		this.currentTrackId = trackId
		setupView()
		loadTrackPoints()
		context.contentResolver.registerContentObserver(
			TrackContentProvider.trackPointsUri(currentTrackId), 
			true, 
			trackpointContentObserver
		)
	}

	private fun setupView() {
		trackPaint.apply {
			color = 0xFF2196F3.toInt() // Material Blue
			strokeWidth = 4f
			style = Paint.Style.STROKE
			isAntiAlias = true
		}
		setBackgroundColor(resources.getColor(android.R.color.white, null))
		text = resources.getString(R.string.displaytrack)
	}

	override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
		if (trackPoints.isNotEmpty()) {
			drawTrackPath()
		}
		super.onSizeChanged(w, h, oldw, oldh)
	}

	override fun onDetachedFromWindow() {
		context.contentResolver.unregisterContentObserver(trackpointContentObserver)
		super.onDetachedFromWindow()
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		if (!trackPath.isEmpty) {
			canvas.drawPath(trackPath, trackPaint)
		}
	}

	private fun loadTrackPoints() {
		trackPoints.clear()
		val cursor = context.contentResolver.query(
			TrackContentProvider.trackPointsUri(currentTrackId),
			arrayOf(
				TrackContentProvider.Schema.COL_LATITUDE,
				TrackContentProvider.Schema.COL_LONGITUDE
			),
			null,
			null,
			TrackContentProvider.Schema.COL_TIMESTAMP + " ASC"
		)
		
		cursor?.use { c ->
			val latIndex = c.getColumnIndex(TrackContentProvider.Schema.COL_LATITUDE)
			val lonIndex = c.getColumnIndex(TrackContentProvider.Schema.COL_LONGITUDE)
			
			while (c.moveToNext()) {
				val lat = c.getDouble(latIndex)
				val lon = c.getDouble(lonIndex)
				trackPoints.add(Pair(lat, lon))
			}
		}
		
		Log.d(TAG, "Loaded ${trackPoints.size} track points")
	}

	private fun drawTrackPath() {
		if (trackPoints.isEmpty() || width == 0 || height == 0) return
		
		trackPath.reset()
		
		// 경계 계산
		var minLat = Double.MAX_VALUE
		var maxLat = Double.MIN_VALUE
		var minLon = Double.MAX_VALUE
		var maxLon = Double.MIN_VALUE
		
		trackPoints.forEach { (lat, lon) ->
			if (lat < minLat) minLat = lat
			if (lat > maxLat) maxLat = lat
			if (lon < minLon) minLon = lon
			if (lon > maxLon) maxLon = lon
		}
		
		val latRange = maxLat - minLat
		val lonRange = maxLon - minLon
		val drawWidth = width - (2 * PADDING)
		val drawHeight = height - (2 * PADDING)
		
		if (latRange == 0.0 || lonRange == 0.0) return
		
		// 첫 번째 점으로 경로 시작
		val firstPoint = trackPoints.first()
		val firstX = PADDING + ((firstPoint.second - minLon) / lonRange * drawWidth).toFloat()
		val firstY = PADDING + ((maxLat - firstPoint.first) / latRange * drawHeight).toFloat()
		trackPath.moveTo(firstX, firstY)
		
		// 나머지 점들로 경로 이어감
		for (i in 1 until trackPoints.size) {
			val point = trackPoints[i]
			val x = PADDING + ((point.second - minLon) / lonRange * drawWidth).toFloat()
			val y = PADDING + ((maxLat - point.first) / latRange * drawHeight).toFloat()
			trackPath.lineTo(x, y)
		}
	}
}