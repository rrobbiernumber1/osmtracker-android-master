package net.osmtracker


object OSMTracker {

	object Preferences {
		const val KEY_STORAGE_DIR = "logging.storage.dir"
		const val KEY_VOICEREC_DURATION = "voicerec.duration"
		const val KEY_UI_THEME = "ui.theme"
		const val KEY_GPS_OSSETTINGS = "gps.ossettings"
		const val KEY_GPS_CHECKSTARTUP = "gps.checkstartup"
		const val KEY_GPS_IGNORE_CLOCK = "gps.ignoreclock"
		const val KEY_GPS_LOGGING_INTERVAL = "gps.logging.interval"
		const val KEY_GPS_LOGGING_MIN_DISTANCE = "gps.logging.min_distance"
		const val KEY_USE_BAROMETER = "gpx.use_barometer"
		const val KEY_OUTPUT_FILENAME = "gpx.filename"
		const val KEY_OUTPUT_FILENAME_LABEL = "gpx.filename.label"
		const val KEY_OUTPUT_ACCURACY = "gpx.accuracy"
		const val KEY_OUTPUT_GPX_HDOP_APPROXIMATION = "gpx.hdop.approximation"
		const val KEY_OUTPUT_DIR_PER_TRACK = "gpx.directory_per_track"
		const val KEY_OUTPUT_COMPASS = "gpx.compass_heading"
		const val KEY_UI_PICTURE_SOURCE = "ui.picture.source"
		const val KEY_UI_DISPLAY_KEEP_ON = "ui.display_keep_on"
		const val KEY_SOUND_ENABLED = "sound_enabled"
		const val KEY_UI_ORIENTATION = "ui.orientation"
        // OSM 업로드 제거됨: 인증 키 미사용
		// Intro removed: KEY_DISPLAY_APP_INTRO no longer used

		const val VAL_STORAGE_DIR = "/osmtracker"
		const val VAL_VOICEREC_DURATION = "2"
		const val VAL_UI_THEME = "net.osmtracker:style/DefaultTheme"
		const val VAL_GPS_CHECKSTARTUP = true
		const val VAL_GPS_IGNORE_CLOCK = false
		const val VAL_GPS_LOGGING_INTERVAL = "0"
		const val VAL_GPS_LOGGING_MIN_DISTANCE = "0"
		const val VAL_USE_BAROMETER = false

		const val VAL_OUTPUT_FILENAME_NAME = "name"
		const val VAL_OUTPUT_FILENAME_NAME_DATE = "name_date"
		const val VAL_OUTPUT_FILENAME_DATE_NAME = "date_name"
		const val VAL_OUTPUT_FILENAME_DATE = "date"
		const val VAL_OUTPUT_FILENAME = VAL_OUTPUT_FILENAME_NAME_DATE
		const val VAL_OUTPUT_FILENAME_LABEL = ""

		const val VAL_OUTPUT_ACCURACY_NONE = "none"
		const val VAL_OUTPUT_ACCURACY_WPT_NAME = "wpt_name"
		const val VAL_OUTPUT_ACCURACY_WPT_CMT = "wpt_cmt"
		const val VAL_OUTPUT_ACCURACY = VAL_OUTPUT_ACCURACY_NONE

		const val VAL_OUTPUT_COMPASS_NONE = "none"
		const val VAL_OUTPUT_COMPASS_COMMENT = "comment"
		const val VAL_OUTPUT_COMPASS_EXTENSION = "extension"
		const val VAL_OUTPUT_COMPASS = VAL_OUTPUT_COMPASS_NONE

		const val VAL_OUTPUT_GPX_HDOP_APPROXIMATION = false
		const val VAL_OUTPUT_GPX_OUTPUT_DIR_PER_TRACK = true

		const val VAL_UI_PICTURE_SOURCE_CAMERA = "camera"
		const val VAL_UI_PICTURE_SOURCE_GALLERY = "gallery"
		const val VAL_UI_PICTURE_SOURCE_ASK = "ask"
		const val VAL_UI_PICTURE_SOURCE = VAL_UI_PICTURE_SOURCE_CAMERA

		const val VAL_UI_DISPLAY_KEEP_ON = true
		const val VAL_SOUND_ENABLED = true
		const val VAL_UI_ORIENTATION_NONE = "none"
		const val VAL_UI_ORIENTATION_PORTRAIT = "portrait"
		const val VAL_UI_ORIENTATION_LANDSCAPE = "landscape"
		const val VAL_UI_ORIENTATION = VAL_UI_ORIENTATION_NONE



		// Intro removed: VAL_DISPLAY_APP_INTRO no longer used
	}

	const val PACKAGE_NAME: String = "net.osmtracker"
	const val INTENT_TRACK_WP: String = "$PACKAGE_NAME.intent.TRACK_WP"
	const val INTENT_UPDATE_WP: String = "$PACKAGE_NAME.intent.UPDATE_WP"
	const val INTENT_DELETE_WP: String = "$PACKAGE_NAME.intent.DELETE_WP"
	const val INTENT_START_TRACKING: String = "$PACKAGE_NAME.intent.START_TRACKING"
	const val INTENT_STOP_TRACKING: String = "$PACKAGE_NAME.intent.STOP_TRACKING"
	const val INTENT_KEY_NAME: String = "name"
	const val INTENT_KEY_LINK: String = "link"
	const val INTENT_KEY_UUID: String = "uuid"
	const val HDOP_APPROXIMATION_FACTOR: Int = 4
	const val LONG_PRESS_TIME: Long = 1000L

	object Devices {
		const val NEXUS_S: String = "Nexus S"
	}
}


