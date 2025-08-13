package net.osmtracker.listener

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

class PressureListener : SensorEventListener {

	private var sensorService: SensorManager? = null
	private var lastAtmosphericPressureHPa: Float = 0f

	override fun onSensorChanged(event: android.hardware.SensorEvent) {
		lastAtmosphericPressureHPa = event.values[0]
	}

	override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

	fun register(context: Context, useBarometer: Boolean): Boolean {
		var result = false
		if (useBarometer) {
			sensorService = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
			val pressureSens = sensorService?.getDefaultSensor(Sensor.TYPE_PRESSURE)
			if (pressureSens != null) {
				sensorService?.registerListener(this, pressureSens, SensorManager.SENSOR_DELAY_NORMAL)
				Log.i(TAG, "Registerered for pressure Sensor")
				result = true
			} else {
				Log.w(TAG, "Pressure sensor not found")
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
			Log.v(TAG, "unregistered")
		}
	}

	fun getPressure(): Float = lastAtmosphericPressureHPa

	companion object {
		private const val TAG = "PressureListener"
	}
}


