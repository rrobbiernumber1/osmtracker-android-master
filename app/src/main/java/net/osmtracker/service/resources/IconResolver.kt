package net.osmtracker.service.resources

import android.graphics.drawable.Drawable

interface IconResolver {
	fun getIcon(key: String?): Drawable?
}


