package net.osmtracker.gpx

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.DialogInterface
import android.database.Cursor
import android.media.MediaScannerConnection
import android.os.AsyncTask
import android.preference.PreferenceManager
import android.util.Log
import android.widget.Toast
import net.osmtracker.OSMTracker
import net.osmtracker.R
import net.osmtracker.db.DataHelper
import net.osmtracker.db.TrackContentProvider

import net.osmtracker.util.FileSystemUtils
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.Writer
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone
import java.util.regex.Pattern

abstract class ExportTrackTask(protected var context: Context, vararg trackIds: Long) : AsyncTask<Void, Long, Boolean>() {

    private val pointDateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
    private var trackIds: LongArray = trackIds
    protected var dialog: ProgressDialog? = null
    private var errorMsg: String? = null

    init {
        pointDateFormatter.timeZone = TimeZone.getTimeZone("UTC")
    }

    protected abstract fun getExportDirectory(startDate: Date): File
    protected abstract fun exportMediaFiles(): Boolean
    protected abstract fun updateExportDate(): Boolean

    override fun onPreExecute() {
        dialog = ProgressDialog(context).apply {
            setProgressStyle(ProgressDialog.STYLE_SPINNER)
            isIndeterminate = true
            setCancelable(false)
            setMessage(context.resources.getString(R.string.trackmgr_exporting_prepare))
            show()
        }
    }

    override fun doInBackground(vararg params: Void?): Boolean {
        return try {
            for (trackId in trackIds) exportTrackAsGpx(trackId)
            true
        } catch (e: Exception) {
            Log.e("ExportTrackTask", "Error exporting track", e)
            errorMsg = "Unable to process the track: ${e.message}"
            false
        }
    }

    override fun onProgressUpdate(vararg values: Long?) {
        val dlg = dialog ?: return
        if (values.size == 1) {
            dlg.incrementProgressBy(values[0]?.toInt() ?: 0)
        } else if (values.size == 3) {
            dlg.dismiss()
            val pd = ProgressDialog(context)
            pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            pd.isIndeterminate = false
            pd.setCancelable(false)
            pd.setProgress(0)
            pd.setMax(((values[1] ?: 0L) + (values[2] ?: 0L)).toInt())
            pd.setTitle(context.resources.getString(R.string.trackmgr_exporting).replace("{0}", (values[0] ?: 0L).toString()))
            pd.show()
            dialog = pd
        }
    }

    public override fun onPostExecute(success: Boolean) {
        dialog?.dismiss()
        if (!success) {
            AlertDialog.Builder(context)
                .setTitle(android.R.string.dialog_alert_title)
                .setMessage(
                    context.resources.getString(R.string.trackmgr_export_error)
                        .replace("{0}", errorMsg ?: "")
                )
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setNeutralButton(android.R.string.ok) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
                .show()
        } else {
            Toast.makeText(this.context, R.string.various_export_finished, Toast.LENGTH_SHORT).show()
        }
    }

    protected fun exportTrackAsGpx(trackId: Long) {
        try {
            Log.d("ExportTrackTask", "Starting export for track ID: $trackId")
            val cr: ContentResolver = context.contentResolver
            val c = context.contentResolver.query(
                ContentUris.withAppendedId(TrackContentProvider.CONTENT_URI_TRACK, trackId), null, null, null, null
            )
            
            Log.d("ExportTrackTask", "Track query result: cursor=${c != null}, count=${c?.count ?: 0}")
            
            if (c == null || c.count == 0) {
                throw RuntimeException("Track with ID $trackId not found")
            }
            
            val startDate = Date()
            c.moveToFirst()
            val startDateInMilliseconds = c.getLong(c.getColumnIndex(TrackContentProvider.Schema.COL_START_DATE))
            startDate.time = startDateInMilliseconds
            
            val trackGPXExportDirectory = getExportDirectory(startDate)
            val filenameBase = buildGPXFilename(c, trackGPXExportDirectory)
            val tags = c.getString(c.getColumnIndex(TrackContentProvider.Schema.COL_TAGS))
            val trackDescription = c.getString(c.getColumnIndex(TrackContentProvider.Schema.COL_DESCRIPTION))
            val trackName = c.getString(c.getColumnIndex(TrackContentProvider.Schema.COL_NAME))
            c.close()
            
            val trackFile = File(trackGPXExportDirectory, filenameBase)
            Log.d("ExportTrackTask", "Export directory: ${trackGPXExportDirectory.absolutePath}")
            Log.d("ExportTrackTask", "Filename base: $filenameBase")
            Log.d("ExportTrackTask", "Target file: ${trackFile.absolutePath}")
            
            val trackPointsUri = TrackContentProvider.trackPointsUri(trackId)
            val wayPointsUri = TrackContentProvider.waypointsUri(trackId)
            
            Log.d("ExportTrackTask", "TrackPoints URI: $trackPointsUri")
            Log.d("ExportTrackTask", "WayPoints URI: $wayPointsUri")
            
            val cTrackPoints = cr.query(trackPointsUri, null, null, null, TrackContentProvider.Schema.COL_TIMESTAMP + " asc")
            val cWayPoints = cr.query(wayPointsUri, null, null, null, TrackContentProvider.Schema.COL_TIMESTAMP + " asc")
            
            Log.d("ExportTrackTask", "TrackPoints query result: cursor=${cTrackPoints != null}, count=${cTrackPoints?.count ?: 0}")
            Log.d("ExportTrackTask", "WayPoints query result: cursor=${cWayPoints != null}, count=${cWayPoints?.count ?: 0}")
            
            if (cTrackPoints == null) {
                throw RuntimeException("Failed to query trackpoints for track $trackId")
            }
            if (cWayPoints == null) {
                throw RuntimeException("Failed to query waypoints for track $trackId")
            }
            
            Log.d("ExportTrackTask", "Exporting track $trackId: ${cTrackPoints.count} trackpoints, ${cWayPoints.count} waypoints")
            
            publishProgress(trackId, cTrackPoints.count.toLong(), cWayPoints.count.toLong())
            try {
                writeGpxFile(trackId, trackName, tags, trackDescription, cTrackPoints, cWayPoints, trackFile)
                if (updateExportDate()) DataHelper.setTrackExportDate(trackId, System.currentTimeMillis(), cr)
            } catch (ioe: IOException) {
                throw RuntimeException("Failed to write GPX file: ${ioe.message}")
            } finally {
                cTrackPoints.close()
                cWayPoints.close()
            }
            
            val files = ArrayList<String>()
            for (file in trackGPXExportDirectory.listFiles() ?: emptyArray()) {
                files.add(file.absolutePath)
            }
            MediaScannerConnection.scanFile(context, files.toTypedArray(), null, null)
            
        } catch (e: Exception) {
            Log.e("ExportTrackTask", "Error in exportTrackAsGpx for track $trackId", e)
            throw e
        }
    }

    @Throws(IOException::class)
    private fun writeGpxFile(trackId: Long, trackName: String?, tags: String?, trackDescription: String?, cTrackPoints: Cursor, cWayPoints: Cursor, target: File) {
        Log.d("ExportTrackTask", "Starting writeGpxFile for track $trackId")
        Log.d("ExportTrackTask", "Target file: ${target.absolutePath}")
        Log.d("ExportTrackTask", "TrackPoints count: ${cTrackPoints.count}")
        Log.d("ExportTrackTask", "WayPoints count: ${cWayPoints.count}")
        
        val accuracyOutput = PreferenceManager.getDefaultSharedPreferences(context).getString(
            OSMTracker.Preferences.KEY_OUTPUT_ACCURACY, OSMTracker.Preferences.VAL_OUTPUT_ACCURACY
        )
        val fillHDOP = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
            OSMTracker.Preferences.KEY_OUTPUT_GPX_HDOP_APPROXIMATION, OSMTracker.Preferences.VAL_OUTPUT_GPX_HDOP_APPROXIMATION
        )
        var writer: Writer? = null
        try {
            Log.d("ExportTrackTask", "Creating BufferedWriter for file: ${target.absolutePath}")
            writer = BufferedWriter(FileWriter(target))
            writer.write(XML_HEADER + "\n")
            writer.write(TAG_GPX + "\n")
            writer.write("\t<metadata>\n")
            if (trackName != null && trackName != "") writer.write("\t\t<name>$CDATA_START$trackName$CDATA_END</name>\n")
            // Get realtime stats from database
            var totalTime = 0.0
            var movingTime = 0.0
            var elevationGain = 0.0
            var activityType = "Walking"
            
            // Get track info from database
            val trackCursor = context.contentResolver.query(
                ContentUris.withAppendedId(TrackContentProvider.CONTENT_URI_TRACK, trackId), 
                null, null, null, null
            )
            if (trackCursor != null && trackCursor.moveToFirst()) {
                val totalTimeColIndex = trackCursor.getColumnIndex(TrackContentProvider.Schema.COL_TOTAL_TIME)
                val movingTimeColIndex = trackCursor.getColumnIndex(TrackContentProvider.Schema.COL_MOVING_TIME)
                val elevationGainColIndex = trackCursor.getColumnIndex(TrackContentProvider.Schema.COL_ELEVATION_GAIN)
                val activityTypeColIndex = trackCursor.getColumnIndex(TrackContentProvider.Schema.COL_ACTIVITY_TYPE)
                
                if (totalTimeColIndex >= 0) {
                    totalTime = trackCursor.getLong(totalTimeColIndex).toDouble()
                }
                if (movingTimeColIndex >= 0) {
                    movingTime = trackCursor.getLong(movingTimeColIndex).toDouble()
                }
                if (elevationGainColIndex >= 0) {
                    elevationGain = trackCursor.getDouble(elevationGainColIndex)
                }
                if (activityTypeColIndex >= 0) {
                    activityType = trackCursor.getString(activityTypeColIndex) ?: "Walking"
                }
                trackCursor.close()
            }
            
            // Fallback to calculated values if database values are not available
            if (totalTime == 0.0 && cTrackPoints.count > 0) {
                cTrackPoints.moveToFirst()
                val timestampColIndex = cTrackPoints.getColumnIndex(TrackContentProvider.Schema.COL_TIMESTAMP)
                if (timestampColIndex >= 0) {
                    val startTime = cTrackPoints.getLong(timestampColIndex)
                    cTrackPoints.moveToLast()
                    val endTime = cTrackPoints.getLong(timestampColIndex)
                    totalTime = (endTime - startTime) / 1000.0 // Convert to seconds
                    movingTime = totalTime // Set moving time equal to total time as requested
                    cTrackPoints.moveToFirst() // Reset cursor position
                }
            }
            
            // Add metadata time
            if (cTrackPoints.count > 0) {
                cTrackPoints.moveToFirst()
                val timestampColIndex = cTrackPoints.getColumnIndex(TrackContentProvider.Schema.COL_TIMESTAMP)
                if (timestampColIndex >= 0) {
                    val startTime = cTrackPoints.getLong(timestampColIndex)
                    writer.write("\t\t<time>" + pointDateFormatter.format(Date(startTime)) + "</time>\n")
                }
                cTrackPoints.moveToFirst() // Reset cursor position
            }
            
            // Create JSON description in PairPlay format
            val jsonDesc = """{"total_time":$totalTime,"elevation_gain":$elevationGain,"activity_type":"$activityType","moving_time":$movingTime,"created_by":"PairPlay_realtime_tracking"}"""
            writer.write("\t\t<desc>$jsonDesc</desc>\n")
            writer.write("\t</metadata>\n")
            writeWayPoints(writer, cWayPoints, accuracyOutput!!, fillHDOP)
            writeTrackPoints(context.resources.getString(R.string.gpx_track_name), writer, cTrackPoints, fillHDOP)
            writer.write("</gpx>")
        } finally {
            writer?.close()
        }
    }

    @Throws(IOException::class)
    private fun writeTrackPoints(trackName: String, fw: Writer, c: Cursor, fillHDOP: Boolean) {
        var dialogUpdateThreshold = c.count / 100
        if (dialogUpdateThreshold == 0) dialogUpdateThreshold++
        
        // Get column indices safely
        val latColIndex = c.getColumnIndex(TrackContentProvider.Schema.COL_LATITUDE)
        val lonColIndex = c.getColumnIndex(TrackContentProvider.Schema.COL_LONGITUDE)
        val eleColIndex = c.getColumnIndex(TrackContentProvider.Schema.COL_ELEVATION)
        val timestampColIndex = c.getColumnIndex(TrackContentProvider.Schema.COL_TIMESTAMP)
        
        // Check if required columns exist
        if (latColIndex < 0 || lonColIndex < 0 || timestampColIndex < 0) {
            throw RuntimeException("Required columns not found in trackpoint cursor. lat=$latColIndex, lon=$lonColIndex, timestamp=$timestampColIndex")
        }
        
        fw.write("\t<trk>\n")
        fw.write("\t\t<type>Walking</type>\n")
        fw.write("\t\t<trkseg>\n")
        var i = 0
        c.moveToFirst()
        while (!c.isAfterLast) {
            val out = StringBuffer()
            out.append("\t\t\t<trkpt lat=\"" + c.getDouble(latColIndex) + "\" lon=\"" + c.getDouble(lonColIndex) + "\">\n")
            if (eleColIndex >= 0 && !c.isNull(eleColIndex)) {
                out.append("\t\t\t\t<ele>" + c.getDouble(eleColIndex) + "</ele>\n")
            }
            out.append("\t\t\t\t<time>" + pointDateFormatter.format(Date(c.getLong(timestampColIndex))) + "</time>\n")
            out.append("\t\t\t</trkpt>\n")
            fw.write(out.toString())
            if (i % dialogUpdateThreshold == 0) publishProgress(dialogUpdateThreshold.toLong())
            i++
            c.moveToNext()
        }
        fw.write("\t\t</trkseg>\n")
        fw.write("\t</trk>\n")
    }

    @Throws(IOException::class)
    private fun writeWayPoints(fw: Writer, c: Cursor, accuracyInfo: String, fillHDOP: Boolean) {
        var dialogUpdateThreshold = c.count / 100
        if (dialogUpdateThreshold == 0) dialogUpdateThreshold++
        val meterUnit = context.resources.getString(R.string.various_unit_meters)
        val accuracy = context.resources.getString(R.string.various_accuracy)
        
        // Get column indices safely
        val latColIndex = c.getColumnIndex(TrackContentProvider.Schema.COL_LATITUDE)
        val lonColIndex = c.getColumnIndex(TrackContentProvider.Schema.COL_LONGITUDE)
        val eleColIndex = c.getColumnIndex(TrackContentProvider.Schema.COL_ELEVATION)
        val timestampColIndex = c.getColumnIndex(TrackContentProvider.Schema.COL_TIMESTAMP)
        val nameColIndex = c.getColumnIndex(TrackContentProvider.Schema.COL_NAME)
        val accuracyColIndex = c.getColumnIndex(TrackContentProvider.Schema.COL_ACCURACY)
        val linkColIndex = c.getColumnIndex(TrackContentProvider.Schema.COL_LINK)
        val satellitesColIndex = c.getColumnIndex(TrackContentProvider.Schema.COL_NBSATELLITES)
        
        // Check if required columns exist
        if (latColIndex < 0 || lonColIndex < 0 || timestampColIndex < 0) {
            throw RuntimeException("Required columns not found in waypoint cursor. lat=$latColIndex, lon=$lonColIndex, timestamp=$timestampColIndex")
        }
        
        var i = 0
        c.moveToFirst()
        while (!c.isAfterLast) {
            val out = StringBuilder()
            out.append("\t<wpt lat=\"" + c.getDouble(latColIndex) + "\" lon=\"" + c.getDouble(lonColIndex) + "\">\n")
            if (eleColIndex >= 0 && !c.isNull(eleColIndex)) {
                out.append("\t\t<ele>" + c.getDouble(eleColIndex) + "</ele>\n")
            }
            out.append("\t\t<time>" + pointDateFormatter.format(Date(c.getLong(timestampColIndex))) + "</time>\n")
            val name = if (nameColIndex >= 0) c.getString(nameColIndex) else ""
            if (OSMTracker.Preferences.VAL_OUTPUT_ACCURACY_NONE != accuracyInfo && accuracyColIndex >= 0 && !c.isNull(accuracyColIndex)) {
                if (OSMTracker.Preferences.VAL_OUTPUT_ACCURACY_WPT_NAME == accuracyInfo) {
                    out.append("\t\t<name>$CDATA_START$name (${"" + c.getDouble(accuracyColIndex)}$meterUnit)$CDATA_END</name>\n")
                } else if (OSMTracker.Preferences.VAL_OUTPUT_ACCURACY_WPT_CMT == accuracyInfo) {
                    out.append("\t\t<name>$CDATA_START$name$CDATA_END</name>\n")
                    out.append("\t\t<cmt>$CDATA_START$accuracy: ${"" + c.getDouble(accuracyColIndex)}$meterUnit$CDATA_END</cmt>\n")
                } else {
                    out.append("\t\t<name>$CDATA_START$name$CDATA_END</name>\n")
                }
            }
            val link = if (linkColIndex >= 0) c.getString(linkColIndex) else null
            if (link != null) {
                out.append("\t\t<link href=\"" + URLEncoder.encode(link) + "\">\n")
                out.append("\t\t\t<text>$link</text>\n")
                out.append("\t\t</link>\n")
            }
            if (satellitesColIndex >= 0 && !c.isNull(satellitesColIndex)) {
                out.append("\t\t<sat>" + c.getInt(satellitesColIndex) + "</sat>\n")
            }
            if (fillHDOP && accuracyColIndex >= 0 && !c.isNull(accuracyColIndex)) {
                out.append("\t\t<hdop>" + c.getDouble(accuracyColIndex) / OSMTracker.HDOP_APPROXIMATION_FACTOR + "</hdop>\n")
            }
            out.append("\t</wpt>\n")
            fw.write(out.toString())
            if (i % dialogUpdateThreshold == 0) publishProgress(dialogUpdateThreshold.toLong())
            i++
            c.moveToNext()
        }
    }

    open fun buildGPXFilename(cursor: Cursor, parentDirectory: File): String {
        val desiredOutputFormat = PreferenceManager.getDefaultSharedPreferences(context).getString(
            OSMTracker.Preferences.KEY_OUTPUT_FILENAME, OSMTracker.Preferences.VAL_OUTPUT_FILENAME
        )
        val trackStartDate = cursor.getLong(cursor.getColumnIndex(TrackContentProvider.Schema.COL_START_DATE))
        val formattedTrackStartDate = DataHelper.FILENAME_FORMATTER.format(Date(trackStartDate))
        var trackName = cursor.getString(cursor.getColumnIndex(TrackContentProvider.Schema.COL_NAME))
        if (trackName != null && trackName.isNotEmpty()) trackName = sanitizeTrackName(trackName) else trackName = null
        var firstGpxFilename = formatGpxFilename(desiredOutputFormat!!, trackName, formattedTrackStartDate)
        firstGpxFilename = FileSystemUtils.getUniqueChildNameFor(parentDirectory, firstGpxFilename, DataHelper.EXTENSION_GPX)
        return firstGpxFilename
    }

    open fun formatGpxFilename(desiredOutputFormat: String, sanitizedTrackName: String?, formattedTrackStartDate: String): String {
        var result = ""
        var exportLabelName = PreferenceManager.getDefaultSharedPreferences(context).getString(
            OSMTracker.Preferences.KEY_OUTPUT_FILENAME_LABEL, OSMTracker.Preferences.VAL_OUTPUT_FILENAME_LABEL
        )
        if (exportLabelName == null) exportLabelName = OSMTracker.Preferences.VAL_OUTPUT_FILENAME_LABEL
        val thereIsTrackName = sanitizedTrackName != null && sanitizedTrackName.length >= 1
        when (desiredOutputFormat) {
            OSMTracker.Preferences.VAL_OUTPUT_FILENAME_NAME -> result += if (thereIsTrackName) sanitizedTrackName else formattedTrackStartDate
            OSMTracker.Preferences.VAL_OUTPUT_FILENAME_NAME_DATE -> {
                if (thereIsTrackName) result += if (sanitizedTrackName == formattedTrackStartDate) sanitizedTrackName else sanitizedTrackName + "_" + formattedTrackStartDate else result += formattedTrackStartDate
            }
            OSMTracker.Preferences.VAL_OUTPUT_FILENAME_DATE_NAME -> {
                if (thereIsTrackName) result += if (sanitizedTrackName == formattedTrackStartDate) formattedTrackStartDate else formattedTrackStartDate + "_" + sanitizedTrackName else result += formattedTrackStartDate
            }
            OSMTracker.Preferences.VAL_OUTPUT_FILENAME_DATE -> result += formattedTrackStartDate
        }
        if (exportLabelName != "") result += "_" + exportLabelName
        return result
    }

    fun sanitizeTrackName(trackName: String): String {
        // 테스트 기대에 맞게 ':'는 ';'로, 나머지 블랙리스트 문자는 '_'
        val first = trackName.replace(":", ";")
        return FILENAME_CHARS_BLACKLIST_PATTERN.matcher(first).replaceAll("_")
    }

    fun getErrorMsg(): String? = errorMsg

    @JvmName("getExportDialog")
    fun getExportDialog(): ProgressDialog? = dialog



    companion object {
        private const val TAG = "ExportTrackTask"
        private val FILENAME_CHARS_BLACKLIST_PATTERN = Pattern.compile("[ '\"/\\\\*?~@<>]")
        private const val XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        private const val CDATA_START = "<![CDATA["
        private const val CDATA_END = "]]>"
        private const val TAG_GPX = "<gpx xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns=\"http://www.topografix.com/GPX/1/1\" xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd\" version=\"1.1\" creator=\"PairPlay 기록하기 for android\">"
    }
}


