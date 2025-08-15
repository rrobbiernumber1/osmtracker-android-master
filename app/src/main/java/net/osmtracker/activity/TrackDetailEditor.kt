package net.osmtracker.activity

import android.app.Activity
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import net.osmtracker.R
import net.osmtracker.db.DataHelper
import net.osmtracker.db.TrackContentProvider
import net.osmtracker.db.model.Track
import java.util.Date

abstract class TrackDetailEditor : Activity() {

	protected var trackId: Long = 0
	protected lateinit var etName: EditText
	protected lateinit var etDescription: EditText
	protected lateinit var etTags: EditText
	protected var fieldsMandatory = false

	protected fun onCreate(savedInstanceState: Bundle?, viewResId: Int, trackId: Long) {
		super.onCreate(savedInstanceState)
		this.trackId = trackId
		setContentView(viewResId)
		title = title.toString() + ": #" + trackId
		etName = findViewById(R.id.trackdetail_item_name)
		etDescription = findViewById(R.id.trackdetail_item_description)
		etTags = findViewById(R.id.trackdetail_item_tags)
	}

    protected fun bindTrack(t: Track) {
		if (etName.length() == 0) {
            etName.setText(t.getDisplayName())
		}
        etDescription.setText(t.getDescription())
        etTags.setText(t.getCommaSeparatedTags())
	}

	protected fun save(): Boolean {
		etDescription.error = null
		if (fieldsMandatory) {
			if (etDescription.text.length < 1) {
				etDescription.error = resources.getString(R.string.trackdetail_description_mandatory)
				return false
			}
		}
		val trackUri: Uri = ContentUris.withAppendedId(TrackContentProvider.CONTENT_URI_TRACK, trackId)
		val values = ContentValues()
		val cursor: Cursor? = contentResolver.query(trackUri, null, null, null, null)
		var startDateLong: Long = 0
		var tname = ""
		if (cursor != null && cursor.moveToFirst()) {
			startDateLong = cursor.getLong(cursor.getColumnIndex(TrackContentProvider.Schema.COL_START_DATE))
			tname = cursor.getString(cursor.getColumnIndex(TrackContentProvider.Schema.COL_NAME))
			cursor.close()
		}
		val startDate = Date(startDateLong)
		var nameToSave = etName.text.toString().trim { it <= ' ' }
		if (nameToSave.length == 0) nameToSave = DataHelper.FILENAME_FORMATTER.format(startDate)
		values.put(TrackContentProvider.Schema.COL_NAME, nameToSave)
		values.put(TrackContentProvider.Schema.COL_DESCRIPTION, etDescription.text.toString().trim { it <= ' ' })
		values.put(TrackContentProvider.Schema.COL_TAGS, etTags.text.toString().trim { it <= ' ' })
		contentResolver.update(trackUri, values, null, null)
		return true
	}
}


