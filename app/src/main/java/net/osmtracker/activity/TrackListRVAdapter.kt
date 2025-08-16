package net.osmtracker.activity

import android.content.Context
import android.database.Cursor
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.NonNull
import androidx.recyclerview.widget.RecyclerView
import net.osmtracker.R
import net.osmtracker.db.TrackContentProvider
import net.osmtracker.db.model.Track

class TrackListRVAdapter(
	private val context: Context,
	private val cursor: Cursor,
	private val mHandler: TrackListRecyclerViewAdapterListener
) : RecyclerView.Adapter<TrackListRVAdapter.TrackItemVH>() {

	interface TrackListRecyclerViewAdapterListener {
		fun onClick(trackId: Long)
		fun onCreateContextMenu(contextMenu: ContextMenu, view: View, contextMenuInfo: ContextMenu.ContextMenuInfo?, trackId: Long)
	}

	inner class TrackItemVH(view: View) : RecyclerView.ViewHolder(view), View.OnClickListener,
		View.OnCreateContextMenuListener {
		private val vId: TextView = view.findViewById(R.id.trackmgr_item_id)
		private val vNameOrStartDate: TextView = view.findViewById(R.id.trackmgr_item_nameordate)
		private val vWps: TextView = view.findViewById(R.id.trackmgr_item_wps)
		private val vTps: TextView = view.findViewById(R.id.trackmgr_item_tps)
        private val vStatus: ImageView = view.findViewById(R.id.trackmgr_item_statusicon)

		init {
			view.setOnClickListener(this)
			view.setOnCreateContextMenuListener(this)
		}

		fun getvId(): TextView { return vId }
		fun getvNameOrStartDate(): TextView { return vNameOrStartDate }
		fun getvWps(): TextView { return vWps }
		fun getvTps(): TextView { return vTps }
		fun getvStatus(): ImageView { return vStatus }
        
		override fun onClick(v: View) {
			val trackId = java.lang.Long.parseLong(getvId().text.toString())
			mHandler.onClick(trackId)
		}

		override fun onCreateContextMenu(contextMenu: ContextMenu, view: View, contextMenuInfo: ContextMenu.ContextMenuInfo?) {
			val trackId = java.lang.Long.parseLong(getvId().text.toString())
			mHandler.onCreateContextMenu(contextMenu, view, contextMenuInfo, trackId)
		}
	}

	@NonNull
	override fun onCreateViewHolder(@NonNull parent: ViewGroup, viewType: Int): TrackItemVH {
		return TrackItemVH(LayoutInflater.from(parent.context).inflate(R.layout.tracklist_item, parent, false))
	}

	override fun onBindViewHolder(@NonNull holder: TrackItemVH, position: Int) {
		cursor.moveToPosition(position)
		bindView(holder.itemView, context, cursor)
	}

	override fun getItemCount(): Int {
		return cursor.count
	}

	private fun bindView(view: View, context: Context, cursor: Cursor): View {
		val vId = view.findViewById<TextView>(R.id.trackmgr_item_id)
		val vNameOrStartDate = view.findViewById<TextView>(R.id.trackmgr_item_nameordate)
		val vWps = view.findViewById<TextView>(R.id.trackmgr_item_wps)
		val vTps = view.findViewById<TextView>(R.id.trackmgr_item_tps)
		val vStatus = view.findViewById<ImageView>(R.id.trackmgr_item_statusicon)
		
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
		
		val trackId = cursor.getLong(cursor.getColumnIndex(TrackContentProvider.Schema.COL_ID))
		val strTrackId = java.lang.Long.toString(trackId)
		vId.text = strTrackId
		val t = Track.build(trackId, cursor, context.contentResolver, false)
		vTps.text = Integer.toString(t.getTpCount())
		vWps.text = Integer.toString(t.getWpCount())
		vNameOrStartDate.text = t.getDisplayName()
		return view
	}

	fun getCursor(): Cursor = cursor
}


