package net.osmtracker.util

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import net.osmtracker.OSMTracker
import net.osmtracker.R
import net.osmtracker.activity.TrackLogger
import net.osmtracker.layout.DisablableTableLayout
import net.osmtracker.layout.UserDefinedLayout
import net.osmtracker.listener.PageButtonOnClickListener
import net.osmtracker.listener.StillImageOnClickListener
import net.osmtracker.listener.TagButtonOnClickListener
import net.osmtracker.listener.TextNoteOnClickListener
import net.osmtracker.listener.VoiceRecOnClickListener
import net.osmtracker.service.resources.IconResolver
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException

class UserDefinedLayoutReader(
	udl: UserDefinedLayout,
	private val context: Context,
	tl: TrackLogger,
	private val currentTrackId: Long,
	private val parser: XmlPullParser,
	private val iconResolver: IconResolver
) {

	private val layouts: HashMap<String, ViewGroup> = HashMap()
	private val resources: Resources = context.resources
	private val orientation: Int = resources.configuration.orientation
	private val textNoteOnClickListener = TextNoteOnClickListener(tl)
	private val voiceRecordOnClickListener = VoiceRecOnClickListener(tl)
	private val stillImageOnClickListener = StillImageOnClickListener(tl)
	private val userDefinedLayout: UserDefinedLayout = udl

	private var currentLayoutIconPos: Int = ICON_POS_AUTO

	@Throws(XmlPullParserException::class, IOException::class)
	fun parseLayout(): HashMap<String, ViewGroup> {
		var eventType = parser.eventType
		while (eventType != XmlPullParser.END_DOCUMENT) {
			when (eventType) {
				XmlPullParser.START_TAG -> {
					val tagName = parser.name
					if (XmlSchema.TAG_LAYOUT == tagName) inflateLayout()
				}
			}
			eventType = parser.next()
		}
		return layouts
	}

	@Throws(IOException::class, XmlPullParserException::class)
	private fun inflateLayout() {
		val layoutName = parser.getAttributeValue(null, XmlSchema.ATTR_NAME)
		val layoutIconPosValue = parser.getAttributeValue(null, XmlSchema.ATTR_ICONPOS)

		currentLayoutIconPos = when (layoutIconPosValue) {
			XmlSchema.ATTR_VAL_ICONPOS_TOP -> ICON_POS_TOP
			XmlSchema.ATTR_VAL_ICONPOS_RIGHT -> ICON_POS_RIGHT
			XmlSchema.ATTR_VAL_ICONPOS_BOTTOM -> ICON_POS_BOTTOM
			XmlSchema.ATTR_VAL_ICONPOS_LEFT -> ICON_POS_LEFT
			else -> ICON_POS_AUTO
		}

		val tblLayout = DisablableTableLayout(context)
		tblLayout.layoutParams = LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.FILL_PARENT,
			LinearLayout.LayoutParams.FILL_PARENT,
			1f
		)

		var currentTagName: String? = null
		while (XmlSchema.TAG_LAYOUT != currentTagName) {
			when (parser.next()) {
				XmlPullParser.START_TAG -> if (XmlSchema.TAG_ROW == parser.name) inflateRow(tblLayout)
				XmlPullParser.END_TAG -> currentTagName = parser.name
			}
		}

		layouts[layoutName] = tblLayout
	}

	@Throws(XmlPullParserException::class, IOException::class)
	private fun inflateRow(layout: TableLayout) {
		val tblRow = TableRow(layout.context)
		tblRow.layoutParams = TableLayout.LayoutParams(
			TableLayout.LayoutParams.FILL_PARENT,
			TableLayout.LayoutParams.FILL_PARENT,
			1f
		)

		var currentTagName: String? = null
		while (XmlSchema.TAG_ROW != currentTagName) {
			when (parser.next()) {
				XmlPullParser.START_TAG -> if (XmlSchema.TAG_BUTTON == parser.name) inflateButton(tblRow)
				XmlPullParser.END_TAG -> currentTagName = parser.name
			}
		}

		layout.addView(tblRow)
	}

	fun inflateButton(row: TableRow) {
		val button = Button(row.context)
		button.layoutParams = TableRow.LayoutParams(
			TableRow.LayoutParams.FILL_PARENT,
			TableRow.LayoutParams.FILL_PARENT,
			1f
		)

		val buttonType = parser.getAttributeValue(null, XmlSchema.ATTR_TYPE)
		var buttonIcon: Drawable? = null
		when (buttonType) {
			XmlSchema.ATTR_VAL_PAGE -> {
				button.text = findLabel(parser.getAttributeValue(null, XmlSchema.ATTR_LABEL), resources)
				buttonIcon = iconResolver.getIcon(parser.getAttributeValue(null, XmlSchema.ATTR_ICON))
				button.setOnClickListener(
					PageButtonOnClickListener(
						userDefinedLayout,
						parser.getAttributeValue(null, XmlSchema.ATTR_TARGETLAYOUT)
					)
				)
			}
			XmlSchema.ATTR_VAL_TAG -> {
				button.text = findLabel(parser.getAttributeValue(null, XmlSchema.ATTR_LABEL), resources)
				buttonIcon = iconResolver.getIcon(parser.getAttributeValue(null, XmlSchema.ATTR_ICON))
				button.setOnClickListener(TagButtonOnClickListener(currentTrackId))
			}
			XmlSchema.ATTR_VAL_VOICEREC -> {
				button.text = resources.getString(R.string.gpsstatus_record_voicerec)
				buttonIcon = resources.getDrawable(R.drawable.voice_32x32)
				button.setOnClickListener(voiceRecordOnClickListener)
			}
			XmlSchema.ATTR_VAL_TEXTNOTE -> {
				button.text = resources.getString(R.string.gpsstatus_record_textnote)
				buttonIcon = resources.getDrawable(R.drawable.text_32x32)
				button.setOnClickListener(textNoteOnClickListener)
			}
			XmlSchema.ATTR_VAL_PICTURE -> {
				button.text = resources.getString(R.string.gpsstatus_record_stillimage)
				buttonIcon = resources.getDrawable(R.drawable.camera_32x32)
				button.setOnClickListener(stillImageOnClickListener)
			}
		}

		when (currentLayoutIconPos) {
			ICON_POS_TOP -> button.setCompoundDrawablesWithIntrinsicBounds(null, buttonIcon, null, null)
			ICON_POS_RIGHT -> button.setCompoundDrawablesWithIntrinsicBounds(null, null, buttonIcon, null)
			ICON_POS_BOTTOM -> button.setCompoundDrawablesWithIntrinsicBounds(null, null, null, buttonIcon)
			ICON_POS_LEFT -> button.setCompoundDrawablesWithIntrinsicBounds(buttonIcon, null, null, null)
			ICON_POS_AUTO -> if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
				button.setCompoundDrawablesWithIntrinsicBounds(buttonIcon, null, null, null)
			} else {
				button.setCompoundDrawablesWithIntrinsicBounds(null, buttonIcon, null, null)
			}
		}

		row.addView(button)
	}

	private fun findLabel(text: String?, r: Resources): String? {
		if (text != null && text.startsWith("@")) {
			val resId = resources.getIdentifier(text.replace("@", ""), null, OSMTracker.PACKAGE_NAME)
			if (resId != 0) return resources.getString(resId)
		}
		return text
	}

	private object XmlSchema {
		const val TAG_LAYOUT = "layout"
		const val TAG_ROW = "row"
		const val TAG_BUTTON = "button"

		const val ATTR_NAME = "name"
		const val ATTR_TYPE = "type"
		const val ATTR_LABEL = "label"
		const val ATTR_TARGETLAYOUT = "targetlayout"
		const val ATTR_ICON = "icon"
		const val ATTR_ICONPOS = "iconpos"

		const val ATTR_VAL_TAG = "tag"
		const val ATTR_VAL_PAGE = "page"
		const val ATTR_VAL_VOICEREC = "voicerec"
		const val ATTR_VAL_TEXTNOTE = "textnote"
		const val ATTR_VAL_PICTURE = "picture"

		const val ATTR_VAL_ICONPOS_TOP = "top"
		const val ATTR_VAL_ICONPOS_RIGHT = "right"
		const val ATTR_VAL_ICONPOS_BOTTOM = "bottom"
		const val ATTR_VAL_ICONPOS_LEFT = "left"
	}

	companion object {
		private const val ICON_POS_AUTO = 0
		private const val ICON_POS_TOP = 1
		private const val ICON_POS_RIGHT = 2
		private const val ICON_POS_BOTTOM = 3
		private const val ICON_POS_LEFT = 4
	}
}


