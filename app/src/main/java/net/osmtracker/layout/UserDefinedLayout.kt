package net.osmtracker.layout

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.LinearLayout
import net.osmtracker.OSMTracker
import net.osmtracker.R
import net.osmtracker.activity.TrackLogger
import net.osmtracker.service.resources.AppResourceIconResolver
import net.osmtracker.service.resources.ExternalDirectoryIconResolver
import net.osmtracker.util.UserDefinedLayoutReader
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.util.Stack

class UserDefinedLayout : LinearLayout {

	private var layouts: HashMap<String, ViewGroup> = HashMap()
	private val layoutStack: Stack<String> = Stack()

	constructor(ctx: Context) : super(ctx)
	constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

	@Throws(XmlPullParserException::class, IOException::class)
	constructor(activity: TrackLogger, trackId: Long, xmlLayout: File?) : super(activity) {
		layoutParams = LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT, 1f)

		val udlr: UserDefinedLayoutReader
		val parser: XmlPullParser
		if (xmlLayout == null) {
			parser = resources.getXml(R.xml.default_buttons_layout)
			udlr = UserDefinedLayoutReader(
				this,
				context,
				activity,
				trackId,
				parser,
				AppResourceIconResolver(resources, OSMTracker::class.java.`package`!!.name)
			)
		} else {
			val factory = XmlPullParserFactory.newInstance()
			parser = factory.newPullParser()
			parser.setInput(FileReader(xmlLayout))
			udlr = UserDefinedLayoutReader(
				this,
				context,
				activity,
				trackId,
				parser,
				ExternalDirectoryIconResolver(xmlLayout.parentFile!!)
			)
		}

		layouts = udlr.parseLayout()
		if (layouts.isEmpty() || layouts[ROOT_LAYOUT_NAME] == null) {
			throw IOException("Error in layout file. Is there a layout name '$ROOT_LAYOUT_NAME' defined ?")
		}
		push(ROOT_LAYOUT_NAME)
	}

	fun push(s: String) {
		val layout = layouts[s]
		if (layout != null) {
			layoutStack.push(s)
			if (childCount > 0) removeAllViews()
			addView(layouts[layoutStack.peek()])
		}
	}

	fun pop(): String {
		val out = layoutStack.pop()
		if (childCount > 0) removeAllViews()
		addView(layouts[layoutStack.peek()])
		return out
	}

	fun getStackSize(): Int = layoutStack.size

	override fun setEnabled(enabled: Boolean) {
		super.setEnabled(enabled)
		getChildAt(0).isEnabled = enabled
	}

	private companion object {
		private const val ROOT_LAYOUT_NAME = "root"
	}
}


