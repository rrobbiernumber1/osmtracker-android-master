package net.osmtracker.util

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

object FileSystemUtils {

	private val TAG: String = FileSystemUtils::class.java.simpleName
	private const val DELETE_MAX_RECURSION_DEPTH = 1

	@JvmStatic
	fun copyFile(destinationDirectory: File?, sourceFile: File?, targetFileName: String): Boolean {
		var result = false
		if (destinationDirectory != null && sourceFile != null) {
			val outputFile = File(destinationDirectory.absoluteFile.toString() + File.separator + targetFileName)
			try {
				FileInputStream(sourceFile).use { inputStream ->
					FileOutputStream(outputFile).use { outputStream ->
						val dataBuffer = ByteArray(1024)
						var bytesRead = inputStream.read(dataBuffer)
						while (bytesRead != -1) {
							outputStream.write(dataBuffer, 0, bytesRead)
							bytesRead = inputStream.read(dataBuffer)
						}
						result = true
					}
				}
			} catch (e: IOException) {
				Log.w(TAG, "IOException trying to write copy file [${sourceFile.absolutePath}] to [${destinationDirectory.absolutePath}]: [${e.message}]")
			}
		}
		return result
	}

	@JvmStatic
	fun copyDirectoryContents(destinationDirectory: File?, sourceDirectory: File?): Boolean {
		if (destinationDirectory == null) {
			Log.e(TAG, "Unable to copy: destinationDirectory is null")
			return false
		}
		if (sourceDirectory == null) {
			Log.e(TAG, "Unable to copy: sourceDirectory is null")
			return false
		}
		if (sourceDirectory.exists() && sourceDirectory.isDirectory && destinationDirectory.exists() && destinationDirectory.isDirectory && destinationDirectory.canWrite()) {
			var failedCopy: MutableList<String>? = null
			for (fileToCopy in sourceDirectory.listFiles()!!) {
				Log.i(TAG, "Copying link file [${fileToCopy.name}] from [${sourceDirectory.absolutePath}] to [$destinationDirectory]")
				if (!copyFile(destinationDirectory, fileToCopy, fileToCopy.name)) {
					if (failedCopy == null) failedCopy = mutableListOf()
					failedCopy.add(fileToCopy.name)
				}
			}
			if (failedCopy != null) {
				Log.w(TAG, "Failed to copy the following files: ")
				for (fileName in failedCopy) Log.w(TAG, "\t [$fileName]")
			} else {
				return true
			}
		} else {
			Log.w(TAG, "Unable to copy:\n\tInput dir Exists? [${sourceDirectory.exists()}]\n\tInput dir is directory? [${sourceDirectory.isDirectory}]\n\tOutput dir Exists? [${destinationDirectory.exists()}]\n\tOutput dir is directory [${destinationDirectory.isDirectory}]\n\tOutput dir is writable [${destinationDirectory.canWrite()}]")
		}
		return false
	}

	@JvmStatic
	fun delete(fileToDelete: File, recursive: Boolean): Boolean {
		return delete(fileToDelete, recursive, 0)
	}

	private fun delete(fileToDelete: File, recursive: Boolean, recursionDepth: Int): Boolean {
		if (recursionDepth > DELETE_MAX_RECURSION_DEPTH) {
			Log.w(TAG, "DELETE_MAX_RECURSION_DEPTH ($DELETE_MAX_RECURSION_DEPTH) reached. Directory deletion aborted.")
			return false
		}
		var deleted: Boolean
		if (fileToDelete.isDirectory && recursive) {
			for (child in fileToDelete.listFiles()!!) {
				if (!delete(child, true, recursionDepth + 1)) {
					Log.w(TAG, "deletion of [$child] failed, aborting now...")
					return false
				}
			}
		}
		deleted = fileToDelete.delete()
		val isDir = fileToDelete.isDirectory
		if (deleted) {
			Log.i(TAG, "deleted ${if (isDir) "directory" else "file"} [$fileToDelete]")
		} else {
			Log.w(TAG, "unable to delete ${if (isDir) "directory" else "file"} [$fileToDelete]")
		}
		return deleted
	}

	@JvmStatic
	fun getUniqueChildNameFor(parentDirectory: File, childName: String, childExtension: String): String {
		var serial = 0
		var suffix = ""
		var currentName: String
		var testFile: File
		do {
			currentName = childName + suffix + childExtension
			testFile = File(parentDirectory, currentName)
			serial++
			suffix = "" + serial
		} while (testFile.exists())
		return currentName
	}
}


