package net.osmtracker.activity

import android.content.Context
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.NonNull
import androidx.recyclerview.widget.RecyclerView
import net.osmtracker.R
import net.osmtracker.db.TracklistAdapter

class TrackListRVAdapter(
	private val context: Context,
	cursor: android.database.Cursor,
	private val mHandler: TrackListRecyclerViewAdapterListener
) : RecyclerView.Adapter<TrackListRVAdapter.TrackItemVH>() {

	private val cursorAdapter: TracklistAdapter = TracklistAdapter(context, cursor)

	internal fun getCursorAdapter(): TracklistAdapter = cursorAdapter

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
		private val vUploadStatus: ImageView = view.findViewById(R.id.trackmgr_item_upload_statusicon)

		init {
			view.setOnClickListener(this)
			view.setOnCreateContextMenuListener(this)
		}

		fun getvId(): TextView { return vId }
		fun getvNameOrStartDate(): TextView { return vNameOrStartDate }
		fun getvWps(): TextView { return vWps }
		fun getvTps(): TextView { return vTps }
		fun getvStatus(): ImageView { return vStatus }
		fun getvUploadStatus(): ImageView { return vUploadStatus }

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
		cursorAdapter.cursor.moveToPosition(position)
		cursorAdapter.bindView(holder.itemView, context, cursorAdapter.cursor)
	}

	override fun getItemCount(): Int {
		return cursorAdapter.count
	}
}


