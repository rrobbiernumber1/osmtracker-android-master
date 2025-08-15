package net.osmtracker.db.model

import android.content.ContentResolver
import android.database.Cursor
import net.osmtracker.R
import net.osmtracker.db.TrackContentProvider
import java.text.DateFormat
import java.util.Date

class Track {

	companion object {
		private val DATE_FORMAT: DateFormat = DateFormat.getDateTimeInstance()
		@JvmStatic
		fun build(trackId: Long, tc: Cursor, cr: ContentResolver?, withExtraInformation: Boolean): Track {
			val out = Track()
			out.trackId = trackId
			out.cr = cr
			out.trackDate = tc.getLong(tc.getColumnIndex(TrackContentProvider.Schema.COL_START_DATE))
			out.name = tc.getString(tc.getColumnIndex(TrackContentProvider.Schema.COL_NAME))
			out.description = tc.getString(tc.getColumnIndex(TrackContentProvider.Schema.COL_DESCRIPTION))
			val tags = tc.getString(tc.getColumnIndex(TrackContentProvider.Schema.COL_TAGS))
			if (tags != null && tags != "") {
				out.tags.addAll(tags.split(","))
			}
			out.tpCount = tc.getInt(tc.getColumnIndex(TrackContentProvider.Schema.COL_TRACKPOINT_COUNT))
			out.wpCount = tc.getInt(tc.getColumnIndex(TrackContentProvider.Schema.COL_WAYPOINT_COUNT))
			if (withExtraInformation) {
				out.readExtraInformation()
			}
			return out
		}
	}


	private var name: String? = null
	private var description: String? = null
	private var tags: MutableList<String> = mutableListOf()
	private var tpCount: Int = 0
	private var wpCount: Int = 0
	private var trackDate: Long = 0
	private var trackId: Long = 0
	private var startDate: Long? = null
	private var endDate: Long? = null
	private var startLat: Float? = null
	private var startLong: Float? = null
	private var endLat: Float? = null
	private var endLong: Float? = null
	private var extraInformationRead: Boolean = false
	private var cr: ContentResolver? = null

	private fun readExtraInformation() {
		if (!extraInformationRead) {
			val resolver = cr ?: return
			val startCursor = resolver.query(TrackContentProvider.trackStartUri(trackId), null, null, null, null)
			if (startCursor != null && startCursor.moveToFirst()) {
				startDate = startCursor.getLong(startCursor.getColumnIndex(TrackContentProvider.Schema.COL_TIMESTAMP))
				startLat = startCursor.getFloat(startCursor.getColumnIndex(TrackContentProvider.Schema.COL_LATITUDE))
				startLong = startCursor.getFloat(startCursor.getColumnIndex(TrackContentProvider.Schema.COL_LONGITUDE))
			}
			startCursor?.close()
			val endCursor = resolver.query(TrackContentProvider.trackEndUri(trackId), null, null, null, null)
			if (endCursor != null && endCursor.moveToFirst()) {
				endDate = endCursor.getLong(endCursor.getColumnIndex(TrackContentProvider.Schema.COL_TIMESTAMP))
				endLat = endCursor.getFloat(endCursor.getColumnIndex(TrackContentProvider.Schema.COL_LATITUDE))
				endLong = endCursor.getFloat(endCursor.getColumnIndex(TrackContentProvider.Schema.COL_LONGITUDE))
			}
			endCursor?.close()
			extraInformationRead = true
		}
	}

	fun setTrackId(trackId: Long) {}
	fun getTrackId(): Long = trackId
	fun setTracktDate(tracktDate: Long) { this.trackDate = tracktDate }
	fun setEndDate(endDate: Long) { this.endDate = endDate }
	fun setStartLat(startLat: Float) { this.startLat = startLat }
	fun setTrackDate(trackDate: Long) { this.trackDate = trackDate }
	fun getTrackDate(): Long = trackDate
	fun setStartDate(startDate: Long) { this.startDate = startDate }
	fun getStartDate(): Long? = startDate
	fun setStartLong(startLong: Float) { this.startLong = startLong }
	fun setEndLat(endLat: Float) { this.endLat = endLat }
	fun setEndLong(endLong: Float) { this.endLong = endLong }
	fun getWpCount(): Int = wpCount
	fun getTpCount(): Int = tpCount
	fun setWpCount(wpCount: Int) { this.wpCount = wpCount }
	fun setTpCount(tpCount: Int) { this.tpCount = tpCount }
	fun setName(name: String?) { this.name = name }
	fun setDescription(description: String?) { this.description = description }
	fun getDisplayName(): String { return if (name != null && name!!.isNotEmpty()) name!! else DATE_FORMAT.format(Date(trackDate)) }
	fun getName(): String? = name
	fun getDescription(): String? = description
	fun setTags(tags: List<String>) { this.tags = tags.toMutableList() }
	fun setTags(tags: String) { if (tags.isNotEmpty()) this.tags.addAll(tags.split(",")) }
	fun getTags(): List<String> = tags
	fun getCommaSeparatedTags(): String = tags.joinToString(",")
	fun getStartDateAsString(): String { readExtraInformation(); return if (startDate != null) DATE_FORMAT.format(Date(startDate!!)) else "" }
	fun getEndDateAsString(): String { readExtraInformation(); return if (endDate != null) DATE_FORMAT.format(Date(endDate!!)) else "" }
	fun getStartLat(): Float? { readExtraInformation(); return startLat }
	fun getStartLong(): Float? { readExtraInformation(); return startLong }
	fun getEndLat(): Float? { readExtraInformation(); return endLat }
	fun getEndLong(): Float? { readExtraInformation(); return endLong }
}


