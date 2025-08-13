package net.osmtracker.activity

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.ViewGroup.LayoutParams
import net.osmtracker.OSMTracker
import net.osmtracker.db.TrackContentProvider
import net.osmtracker.util.ThemeValidator
import net.osmtracker.view.DisplayTrackView

class DisplayTrack : Activity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		setTheme(resources.getIdentifier(ThemeValidator.getValidTheme(
				PreferenceManager.getDefaultSharedPreferences(this), resources), null, null))
		super.onCreate(savedInstanceState)

		val trackId = intent.extras!!.getLong(TrackContentProvider.Schema.COL_TRACK_ID)
		val dtv = DisplayTrackView(this, trackId)
		dtv.layoutParams = LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT)
		title = title.toString() + ": #" + intent.extras!!.getLong(TrackContentProvider.Schema.COL_TRACK_ID)
		setContentView(dtv)

		val dtPrefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
		if (!dtPrefs.getBoolean(OSMTracker.Preferences.KEY_UI_ASKED_DISPLAYTRACK_OSM, false)) {
			dtPrefs.edit().putBoolean(OSMTracker.Preferences.KEY_UI_ASKED_DISPLAYTRACK_OSM, true).commit()
			dtv.post {
				AlertDialog.Builder(this@DisplayTrack)
						.setTitle(net.osmtracker.R.string.prefs_displaytrack_osm)
						.setMessage(net.osmtracker.R.string.prefs_displaytrack_osm_summary_ask)
						.setNegativeButton(android.R.string.no, null)
						.setPositiveButton(net.osmtracker.R.string.displaytrack_map, object : DialogInterface.OnClickListener {
							override fun onClick(dialog: DialogInterface, which: Int) {
								PreferenceManager.getDefaultSharedPreferences(this@DisplayTrack).edit()
										.putBoolean(OSMTracker.Preferences.KEY_UI_DISPLAYTRACK_OSM, true).commit()
								val i = Intent(this@DisplayTrack, DisplayTrackMap::class.java)
								i.putExtra(TrackContentProvider.Schema.COL_TRACK_ID, trackId)
								startActivity(i)
								finish()
							}
						})
						.show()
			}
		}
	}
}


