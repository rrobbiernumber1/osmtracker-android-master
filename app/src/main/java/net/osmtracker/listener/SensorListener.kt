package net.osmtracker.listener

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import android.widget.TextView
import net.osmtracker.R
import net.osmtracker.db.DataHelper
import java.text.DecimalFormat

class SensorListener : SensorEventListener {

	private var sensorService: SensorManager? = null
	private var gravity: FloatArray? = null
	private var gravAccuracy: Int = 0
	private var geomag: FloatArray? = null
	private var magAccuracy: Int = 0
	private var azimuth: Float = 0f
	private var pitch: Float = 0f
	private var roll: Float = 0f
	private var accuracy: Int = 0
	private var valid: Boolean = false
	private val inR = FloatArray(9)
	private val outR = FloatArray(9)
	private val I = FloatArray(9)
	private val orientVals = FloatArray(3)
	private var activity: Activity? = null
	private var context: Context? = null

	override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
		Log.v(TAG, "Accuracy changed: sensor:$sensor, accuracy: $accuracy")
	}

	override fun onSensorChanged(event: SensorEvent) {
		var updateRotation = false
		when (event.sensor.type) {
			Sensor.TYPE_ACCELEROMETER -> {
				gravity = event.values.clone()
				gravAccuracy = event.accuracy
				Log.v(TAG, "gravitation sensor accurcay: $gravAccuracy")
				updateRotation = true
			}
			Sensor.TYPE_MAGNETIC_FIELD -> {
				geomag = event.values.clone()
				magAccuracy = event.accuracy
				Log.v(TAG, "magnetic sensor accurcay: $magAccuracy")
				updateRotation = true
			}
			Sensor.TYPE_ORIENTATION -> {
				azimuth = event.values[0]
				pitch = event.values[1]
				roll = event.values[2]
				accuracy = event.accuracy
				valid = true
			}
		}

		valid = if (updateRotation) calcOrientation() else valid
		Log.v(TAG, "new azimuth:  $azimuth, pitch: $pitch, roll: $roll, accuracy: $accuracy, valid: $valid")

		activity?.let { act ->
			val tvHeading = act.findViewById<TextView>(R.id.gpsstatus_record_tvHeading)
			if (tvHeading != null) {
				if (valid) {
					val color = when (accuracy) {
						SensorManager.SENSOR_STATUS_UNRELIABLE -> Color.RED
						SensorManager.SENSOR_STATUS_ACCURACY_LOW -> Color.MAGENTA
						SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> Color.YELLOW
						SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> Color.GREEN
						else -> Color.RED
					}
					tvHeading.setTextColor(color)
					tvHeading.text = act.resources.getString(R.string.various_heading_display)
						.replace("{0}", HEADING_FORMAT.format(azimuth))
				} else {
					tvHeading.setTextColor(Color.GRAY)
					tvHeading.text = act.resources.getString(R.string.various_heading_unknown)
				}
			}
		}
	}

	private fun calcOrientation(): Boolean {
		var success = false
		val localGravity = gravity
		val localGeomag = geomag
		if (localGravity != null && localGeomag != null) {
			success = SensorManager.getRotationMatrix(inR, I, localGravity, localGeomag)
			if (success) {
				SensorManager.remapCoordinateSystem(inR, SensorManager.AXIS_X, SensorManager.AXIS_Z, outR)
				SensorManager.getOrientation(outR, orientVals)
				azimuth = orientVals[0] * RAD_TO_DEG
				pitch = orientVals[1] * RAD_TO_DEG
				roll = orientVals[2] * RAD_TO_DEG
				accuracy = if (magAccuracy < gravAccuracy) magAccuracy else gravAccuracy
			}
		}
		return success
	}

	fun register(activity: Activity): Boolean {
		this.activity = activity
		return register(activity as Context, USE_ORIENTATION_AS_DEFAULT)
	}

	fun register(context: Context): Boolean {
		return register(context, USE_ORIENTATION_AS_DEFAULT)
	}

	fun register(context: Context, useOrientation: Boolean): Boolean {
		this.context = context
		var result: Boolean
		sensorService = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
		if (!useOrientation) {
			val accelSens = sensorService?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
			val magSens = sensorService?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
			if (accelSens != null && magSens != null) {
				sensorService?.registerListener(this, accelSens, SensorManager.SENSOR_DELAY_NORMAL)
				sensorService?.registerListener(this, magSens, SensorManager.SENSOR_DELAY_NORMAL)
				Log.i(TAG, "Registerered for magnetic, acceleration Sensor")
				result = true
			} else {
				Log.w(TAG, "either magnetic or gravitation sensor not found")
				geomag = null
				gravity = null
				unregister()
				result = false
			}
		} else {
			val orSens = sensorService?.getDefaultSensor(Sensor.TYPE_ORIENTATION)
			if (orSens != null) {
				sensorService?.registerListener(this, orSens, SensorManager.SENSOR_DELAY_NORMAL)
				Log.i(TAG, "Registerered for orientation Sensor")
				result = true
			} else {
				Log.w(TAG, "Orientation sensor not found")
				unregister()
				result = false
			}
		}
		return result
	}

	fun unregister() {
		val ss = sensorService
		if (ss != null) {
			ss.unregisterListener(this)
			sensorService = null
			Log.v(TAG, "unregisterd")
		}
	}

	fun getAzimuth(): Float {
		return if (valid) azimuth else DataHelper.AZIMUTH_INVALID
	}

	fun getAccuracy(): Int {
		return accuracy
	}

	companion object {
		private val HEADING_FORMAT = DecimalFormat("0")
		private const val TAG = "SensorListener"
		const val RAD_TO_DEG: Float = 180.0f / 3.141592653589793f
		private const val USE_ORIENTATION_AS_DEFAULT: Boolean = true
	}
}


