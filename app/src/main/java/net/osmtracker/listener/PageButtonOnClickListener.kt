package net.osmtracker.listener

import android.view.View
import net.osmtracker.layout.UserDefinedLayout

class PageButtonOnClickListener(private val rootLayout: UserDefinedLayout, private val targetLayoutName: String?) : View.OnClickListener {
	override fun onClick(v: View) {
		if (targetLayoutName != null) {
			rootLayout.push(targetLayoutName)
		}
	}
}


