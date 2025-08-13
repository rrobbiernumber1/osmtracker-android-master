package net.osmtracker.util

class MercatorProjection(minLat: Double, minLon: Double, maxLat: Double, maxLon: Double, w: Int, h: Int) {

	companion object {
		private const val MAX_LATITUDE: Double = 85.0511
		@JvmField val X: Byte = 0
		@JvmField val LONGITUDE: Byte = 0
		@JvmField val Y: Byte = 1
		@JvmField val LATITUDE: Byte = 1

		@JvmStatic
		fun formatDegreesAsDMS(degreesIn: Float?, isLatitude: Boolean): String {
			var degrees = degreesIn ?: return ""
			val neg: Boolean = if (degrees > 0) false else { degrees = -degrees; true }
			val dms = StringBuffer()
			var n = degrees.toInt()
			dms.append(n)
			dms.append("\u00B0 ")
			degrees = (degrees - n) * 60.0f
			n = degrees.toInt()
			dms.append(n)
			dms.append("' ")
			degrees = (degrees - n) * 60.0f
			n = degrees.toInt()
			dms.append(n)
			dms.append("\" ")
			dms.append(if (isLatitude) if (neg) 'S' else 'N' else if (neg) 'W' else 'E')
			return dms.toString()
		}
	}

	private val width: Int
	private val height: Int
	private var internalScale: Double
	private val topX: Double
	private val topY: Double
	private val bottomX: Double
	private val bottomY: Double
	private val dimX: Double
	private val dimY: Double

	init {
		width = w
		height = h
		val rangeX = kotlin.math.abs(convertLongitude(maxLon) - convertLongitude(minLon))
		val rangeY = kotlin.math.abs(convertLatitude(maxLat) - convertLatitude(minLat))
		val scaleX = rangeX / width
		val scaleY = rangeY / height
		internalScale = if (scaleX > scaleY) scaleX else scaleY
		val offsetX = (width * internalScale) - rangeX
		val offsetY = (height * internalScale) - rangeY
		topX = convertLongitude(minLon) - (offsetX / 2)
		topY = convertLatitude(minLat) - (offsetY / 2)
		bottomX = convertLongitude(maxLon) + (offsetX / 2)
		bottomY = convertLatitude(maxLat) + (offsetY / 2)
		dimX = bottomX - topX
		dimY = bottomY - topY
	}

	fun project(longitude: Double, latitude: Double): IntArray {
		val out = IntArray(2)
		out[X.toInt()] = kotlin.math.round(((convertLongitude(longitude) - topX) / dimX) * width).toInt()
		out[Y.toInt()] = kotlin.math.round(height - (((convertLatitude(latitude) - topY) / dimY) * height)).toInt()
		return out
	}

	private fun convertLongitude(longitude: Double): Double {
		return longitude
	}

	private fun convertLatitude(latitudeIn: Double): Double {
		var latitude = latitudeIn
		if (latitude < -MAX_LATITUDE) latitude = -MAX_LATITUDE else if (latitude > MAX_LATITUDE) latitude = MAX_LATITUDE
		return kotlin.math.ln(kotlin.math.tan(Math.PI / 4 + (latitude * Math.PI / 180 / 2))) / (Math.PI / 180)
	}

	@get:JvmName("getScale")
	val scale: Double
		get() = internalScale
}


