package net.osmtracker.view

import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.SystemClock
import android.preference.PreferenceManager
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import net.osmtracker.OSMTracker
import net.osmtracker.R
import net.osmtracker.db.DataHelper
import net.osmtracker.db.TrackContentProvider.Schema
import java.io.File
import java.util.Date
import java.util.UUID

class VoiceRecDialog(private val ctx: Context, private val wayPointTrackId: Long) : ProgressDialog(ctx), MediaRecorder.OnInfoListener {

	private val TAG: String = VoiceRecDialog::class.java.simpleName
	private var wayPointUuid: String? = null
	private var audioManager: AudioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
	private var recordingDuration: Int = -1
	private var isRecording: Boolean = false
	private var mediaRecorder: MediaRecorder? = null
	private var mediaPlayerStart: MediaPlayer? = null
	private var mediaPlayerStop: MediaPlayer? = null
	private var currentOrientation: Int = -1
	private var currentRequestedOrientation: Int = -1
	private var dialogStartTime: Long = 0

	init {
		setTitle(ctx.resources.getString(R.string.tracklogger_voicerec_title))
		setButton(BUTTON_POSITIVE, ctx.resources.getString(R.string.tracklogger_voicerec_stop)) { _: DialogInterface, _: Int ->
			try {
				mediaRecorder?.stop()
			} catch (_: Exception) { }
			this@VoiceRecDialog.dismiss()
		}
	}

	override fun onStart() {
		dialogStartTime = SystemClock.uptimeMillis()
		val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(ctx)
		if (!isRecording) {
			recordingDuration = preferences.getString(
				OSMTracker.Preferences.KEY_VOICEREC_DURATION,
				OSMTracker.Preferences.VAL_VOICEREC_DURATION
			)!!.toInt()
		} else {
			if (recordingDuration <= 0) recordingDuration = OSMTracker.Preferences.VAL_VOICEREC_DURATION.toInt()
		}
		setMessage(ctx.resources.getString(R.string.tracklogger_voicerec_text).replace("{0}", recordingDuration.toString()))
		try {
			currentOrientation = ctx.resources.configuration.orientation
			currentRequestedOrientation = ownerActivity?.requestedOrientation ?: currentOrientation
			ownerActivity?.requestedOrientation = currentOrientation
		} catch (e: Exception) {
			Log.w(TAG, "No OwnerActivity found for this Dialog. Use showDialog within the activity.")
		}
		Log.d(TAG, "onStart() called")
		if (wayPointUuid == null) {
			Log.d(TAG, "onStart() no UUID set, generating a new UUID")
			wayPointUuid = UUID.randomUUID().toString()
			val intent = Intent(OSMTracker.INTENT_TRACK_WP)
			intent.putExtra(Schema.COL_TRACK_ID, wayPointTrackId)
			intent.putExtra(OSMTracker.INTENT_KEY_UUID, wayPointUuid)
			intent.putExtra(OSMTracker.INTENT_KEY_NAME, ctx.resources.getString(R.string.wpt_voicerec))
			intent.setPackage(context.packageName)
			ctx.sendBroadcast(intent)
		}
		if (!isRecording) {
			Log.d(TAG, "onStart() currently not recording, start a new one")
			isRecording = true
			val audioFile = audioFile
			if (audioFile != null) {
				val playSound = preferences.getBoolean(OSMTracker.Preferences.KEY_SOUND_ENABLED, OSMTracker.Preferences.VAL_SOUND_ENABLED)
				unMuteMicrophone()
				if (playSound) {
					mediaPlayerStart = MediaPlayer.create(ctx, R.raw.beepbeep)
					mediaPlayerStart?.isLooping = false
					mediaPlayerStop = MediaPlayer.create(ctx, R.raw.beep)
					mediaPlayerStop?.isLooping = false
				}
				mediaRecorder = MediaRecorder()
				try {
					mediaRecorder!!.setAudioSource(MediaRecorder.AudioSource.MIC)
					mediaRecorder!!.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
					mediaRecorder!!.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
					mediaRecorder!!.setOutputFile(audioFile.absolutePath)
					mediaRecorder!!.setMaxDuration(recordingDuration * 1000)
					mediaRecorder!!.setOnInfoListener(this)
					Log.d(TAG, "onStart() starting mediaRecorder...")
					mediaRecorder!!.prepare()
					mediaRecorder!!.start()
					mediaPlayerStart?.start()
					Log.d(TAG, "onStart() mediaRecorder started...")
				} catch (ioe: Exception) {
					Log.w(TAG, "onStart() voice recording has failed", ioe)
					this.dismiss()
					Toast.makeText(ctx, ctx.resources.getString(R.string.error_voicerec_failed), Toast.LENGTH_SHORT).show()
				}
				val intent = Intent(OSMTracker.INTENT_UPDATE_WP)
				intent.putExtra(Schema.COL_TRACK_ID, wayPointTrackId)
				intent.putExtra(OSMTracker.INTENT_KEY_UUID, wayPointUuid)
				intent.putExtra(OSMTracker.INTENT_KEY_LINK, audioFile.name)
				intent.setPackage(context.packageName)
				ctx.sendBroadcast(intent)
			} else {
				Log.w(TAG, "onStart() no suitable audioFile could be created")
				Toast.makeText(ctx, ctx.resources.getString(R.string.error_voicerec_failed), Toast.LENGTH_SHORT).show()
			}
		}
		super.onStart()
	}

	override fun onInfo(mr: MediaRecorder, what: Int, extra: Int) {
		Log.d(TAG, "onInfo() received mediaRecorder info (${what})")
		when (what) {
			MediaRecorder.MEDIA_RECORDER_ERROR_UNKNOWN,
			MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED,
			MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED -> {
				mediaPlayerStop?.start()
				this.dismiss()
			}
		}
	}

	override fun onStop() {
		Log.d(TAG, "onStop() called")
		safeClose(mediaRecorder, false)
		safeClose(mediaPlayerStart)
		safeClose(mediaPlayerStop)
		wayPointUuid = null
		isRecording = false
		try {
			ownerActivity?.requestedOrientation = currentRequestedOrientation
		} catch (e: Exception) {
			Log.w(TAG, "No OwnerActivity found for this Dialog. Use showDialog within the activity.")
		}
		super.onStop()
	}

	override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
		if (event.downTime > dialogStartTime) {
			when (keyCode) {
				KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_HEADSETHOOK -> {
					this.dismiss()
					return true
				}
			}
		}
		return super.onKeyDown(keyCode, event)
	}

	private fun unMuteMicrophone() {
		Log.v(TAG, "unMuteMicrophone()")
		if (audioManager.isMicrophoneMute) {
			audioManager.isMicrophoneMute = false
		}
	}

	val audioFile: File?
		get() {
			var audioFile: File? = null
			val trackDir = DataHelper.getTrackDirectory(wayPointTrackId, ctx)
			if (!trackDir.exists()) {
				if (!trackDir.mkdirs()) {
					Log.w(TAG, "Directory [${trackDir.absolutePath}] does not exist and cannot be created")
				}
			}
			if (trackDir.exists() && trackDir.canWrite()) {
				audioFile = File(trackDir, DataHelper.FILENAME_FORMATTER.format(Date()) + DataHelper.EXTENSION_3GPP)
			} else {
				Log.w(TAG, "The directory [${trackDir.absolutePath}] will not allow files to be created")
			}
			return audioFile
		}

	private fun safeClose(mp: MediaPlayer?) {
		if (mp != null) {
			try {
				mp.stop()
			} catch (e: Exception) {
				Log.w(TAG, "Failed to stop media player", e)
			} finally {
				mp.release()
			}
		}
	}

	private fun safeClose(mr: MediaRecorder?, stopIt: Boolean) {
		if (mr != null) {
			try {
				if (stopIt) mr.stop()
			} catch (e: Exception) {
				Log.w(TAG, "Failed to stop media recorder", e)
			} finally {
				mr.release()
			}
		}
	}
}


