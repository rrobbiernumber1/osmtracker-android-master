package net.osmtracker.db

import android.content.Context
import android.database.Cursor
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CursorAdapter
import android.widget.TableLayout
import android.widget.TextView
import net.osmtracker.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

class WaypointListAdapter(context: Context, c: Cursor) : CursorAdapter(context, c) {

	companion object {
		@JvmField val DATE_FORMATTER = SimpleDateFormat("HH:mm:ss 'UTC'").apply { timeZone = TimeZone.getTimeZone("UTC") }
	}

	override fun bindView(view: View, context: Context, cursor: Cursor) {
		val tl = view as TableLayout
		bind(cursor, tl, context)
	}

	override fun newView(context: Context, cursor: Cursor, vg: ViewGroup): View {
		val tl = LayoutInflater.from(vg.context).inflate(R.layout.waypointlist_item, vg, false) as TableLayout
		return bind(cursor, tl, context)
	}

	private fun bind(cursor: Cursor, tl: TableLayout, context: Context): View {
		val vName = tl.findViewById<TextView>(R.id.wplist_item_name)
		val vLocation = tl.findViewById<TextView>(R.id.wplist_item_location)
		val vTimestamp = tl.findViewById<TextView>(R.id.wplist_item_timestamp)
		val name = cursor.getString(cursor.getColumnIndex(TrackContentProvider.Schema.COL_NAME))
		vName.text = name
		val locationAsString = StringBuilder()
		locationAsString.append(context.resources.getString(R.string.wplist_latitude) + cursor.getString(cursor.getColumnIndex(TrackContentProvider.Schema.COL_LATITUDE)))
		locationAsString.append(", " + context.resources.getString(R.string.wplist_longitude) + cursor.getString(cursor.getColumnIndex(TrackContentProvider.Schema.COL_LONGITUDE)))
		if (!cursor.isNull(cursor.getColumnIndex(TrackContentProvider.Schema.COL_ELEVATION))) {
			locationAsString.append(", " + context.resources.getString(R.string.wplist_elevation) + cursor.getString(cursor.getColumnIndex(TrackContentProvider.Schema.COL_ELEVATION)))
		}
		if (!cursor.isNull(cursor.getColumnIndex(TrackContentProvider.Schema.COL_ACCURACY))) {
			locationAsString.append(", " + context.resources.getString(R.string.wplist_accuracy) + cursor.getString(cursor.getColumnIndex(TrackContentProvider.Schema.COL_ACCURACY)))
		}
		if (!cursor.isNull(cursor.getColumnIndex(TrackContentProvider.Schema.COL_COMPASS))) {
			locationAsString.append(", " + context.resources.getString(R.string.wplist_compass) + cursor.getString(cursor.getColumnIndex(TrackContentProvider.Schema.COL_COMPASS)))
			locationAsString.append(", " + context.resources.getString(R.string.wplist_compass_accuracy) + cursor.getString(cursor.getColumnIndex(TrackContentProvider.Schema.COL_COMPASS_ACCURACY)))
		}
		vLocation.text = locationAsString.toString()
		val ts = Date(cursor.getLong(cursor.getColumnIndex(TrackContentProvider.Schema.COL_TIMESTAMP)))
		vTimestamp.text = DATE_FORMATTER.format(ts)
		return tl
	}
}


