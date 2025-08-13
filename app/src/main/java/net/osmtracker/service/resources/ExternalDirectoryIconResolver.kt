package net.osmtracker.service.resources

import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import java.io.File

class ExternalDirectoryIconResolver(baseDir: File) : IconResolver {

	private val directory: File

	init {
		if (!baseDir.isDirectory) {
			throw IllegalArgumentException("baseDir must be a directory. $baseDir is not.")
		}
		directory = baseDir
	}

	override fun getIcon(key: String?): Drawable? {
		return if (key == null) {
			null
		} else {
			val iconFile = File(directory, key)
			if (iconFile.exists() && iconFile.canRead()) {
				val iconBitmap = BitmapFactory.decodeFile(iconFile.absolutePath)
				BitmapDrawable(null, iconBitmap)
			} else null
		}
	}
}


