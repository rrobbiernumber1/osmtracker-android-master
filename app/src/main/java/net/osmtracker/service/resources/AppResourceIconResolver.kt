package net.osmtracker.service.resources

import android.content.res.Resources
import android.graphics.drawable.Drawable

class AppResourceIconResolver(private val resources: Resources, private val resourcesPackage: String) : IconResolver {

	private companion object {
		private const val DRAWABLE_TYPE = "drawable"
	}

	override fun getIcon(key: String?): Drawable? {
		if (key != null) {
			val resId = resources.getIdentifier(key, DRAWABLE_TYPE, resourcesPackage)
			if (resId != 0) {
				return resources.getDrawable(resId)
			}
		}
		return null
	}
}


