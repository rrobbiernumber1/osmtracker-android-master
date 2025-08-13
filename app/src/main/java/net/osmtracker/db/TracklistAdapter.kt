package net.osmtracker.db

import android.content.Context
import android.database.Cursor
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CursorAdapter
import android.widget.ImageView
import android.widget.TextView
import net.osmtracker.R
import net.osmtracker.db.model.Track

class TracklistAdapter(context: Context, c: Cursor) : CursorAdapter(context, c) {

	override fun bindView(view: View, context: Context, cursor: Cursor) {
		bind(cursor, view, context)
	}

	override fun newView(context: Context, cursor: Cursor, vg: ViewGroup): View {
		return LayoutInflater.from(vg.context).inflate(R.layout.tracklist_item, vg, false)
	}

	private fun bind(cursor: Cursor, v: View, context: Context): View {
		val vId = v.findViewById<TextView>(R.id.trackmgr_item_id)
		val vNameOrStartDate = v.findViewById<TextView>(R.id.trackmgr_item_nameordate)
		val vWps = v.findViewById<TextView>(R.id.trackmgr_item_wps)
		val vTps = v.findViewById<TextView>(R.id.trackmgr_item_tps)
		val vStatus = v.findViewById<ImageView>(R.id.trackmgr_item_statusicon)
		val vUploadStatus = v.findViewById<ImageView>(R.id.trackmgr_item_upload_statusicon)
		val active = cursor.getInt(cursor.getColumnIndex(TrackContentProvider.Schema.COL_ACTIVE))
		if (TrackContentProvider.Schema.VAL_TRACK_ACTIVE == active) {
			vStatus.setImageResource(android.R.drawable.presence_away)
			vStatus.visibility = View.VISIBLE
		} else if (cursor.isNull(cursor.getColumnIndex(TrackContentProvider.Schema.COL_EXPORT_DATE))) {
			vStatus.visibility = View.GONE
		} else {
			vStatus.setImageResource(android.R.drawable.presence_online)
			vStatus.visibility = View.VISIBLE
		}
		if (cursor.isNull(cursor.getColumnIndex(TrackContentProvider.Schema.COL_OSM_UPLOAD_DATE))) {
			vUploadStatus.visibility = View.GONE
		} else {
			vUploadStatus.setImageResource(android.R.drawable.stat_sys_upload_done)
			vUploadStatus.visibility = View.VISIBLE
		}
		val trackId = cursor.getLong(cursor.getColumnIndex(TrackContentProvider.Schema.COL_ID))
		val strTrackId = java.lang.Long.toString(trackId)
		vId.text = strTrackId
		val t = Track.build(trackId, cursor, context.contentResolver, false)
		vTps.text = Integer.toString(t.getTpCount())
		vWps.text = Integer.toString(t.getWpCount())
		vNameOrStartDate.text = t.getDisplayName()
		return v
	}
}


