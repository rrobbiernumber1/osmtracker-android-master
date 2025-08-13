package net.osmtracker.layout

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TableLayout

class DisablableTableLayout(context: Context) : TableLayout(context) {

	fun setOnClickListenerForAllChild(l: OnClickListener) {
		for (i in 0 until this.childCount) {
			val v = this.getChildAt(i)
			if (v is ViewGroup) {
				for (j in 0 until v.childCount) {
					val subView = v.getChildAt(j)
					subView.setOnClickListener(l)
				}
			} else {
				v.setOnClickListener(l)
			}
		}
	}

	override fun setEnabled(enabled: Boolean) {
		for (i in 0 until this.childCount) {
			val v: View = this.getChildAt(i)
			if (v is ViewGroup) {
				for (j in 0 until v.childCount) {
					val subView = v.getChildAt(j)
					subView.isEnabled = enabled
				}
			} else {
				v.isEnabled = enabled
			}
		}
	}
}


