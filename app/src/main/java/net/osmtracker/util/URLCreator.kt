package net.osmtracker.util

import android.content.Context
import android.preference.PreferenceManager
import net.osmtracker.OSMTracker
import net.osmtracker.activity.Preferences

object URLCreator {

	private const val API_BASE = "https://api.github.com/repos/"
	private const val RAW_CONTENT = "https://raw.githubusercontent.com/"
	private const val USERNAME = 0
	private const val REPO = 1
	private const val BRANCH = 2

	@JvmStatic
	fun createMetadataDirUrl(context: Context): String {
		val ghParams = getGithubParams(context)
		return API_BASE + ghParams[USERNAME] + "/" + ghParams[REPO] + 
				"/contents/layouts/metadata?ref=" + ghParams[BRANCH]
	}

	@JvmStatic
	fun createMetadataFileURL(context: Context, layoutName: String): String {
		val layoutFileName = CustomLayoutsUtils.unconvertFileName(layoutName)
		val ghParams = getGithubParams(context)
		return RAW_CONTENT + ghParams[USERNAME] + "/" + ghParams[REPO] + "/" + ghParams[BRANCH] +
				"/layouts/metadata/" + layoutFileName
	}

	@JvmStatic
	fun createLayoutFileURL(context: Context, layoutFolderName: String, iso: String): String {
		val ghParams = getGithubParams(context)
		return RAW_CONTENT + ghParams[USERNAME] + "/" + ghParams[REPO] + "/" + ghParams[BRANCH] +
				"/layouts/" + layoutFolderName + "/" + iso + Preferences.LAYOUT_FILE_EXTENSION
	}

	@JvmStatic
	fun createIconsDirUrl(context: Context, layoutFolderName: String): String {
		val ghParams = getGithubParams(context)
		return API_BASE + ghParams[USERNAME] + "/" + ghParams[REPO] +
				"/contents/layouts/" + layoutFolderName + "/" + layoutFolderName + "_icons" + "?ref=" + ghParams[BRANCH]
	}

	@JvmStatic
	fun createTestURL(ghUsername: String, repositoryName: String, branchName: String): String {
		return API_BASE + ghUsername + "/" + repositoryName + "/contents/layouts/metadata?ref=" + branchName
	}

	private fun getGithubParams(context: Context): Array<String> {
		val preferences = PreferenceManager.getDefaultSharedPreferences(context)
		val username = preferences.getString(OSMTracker.Preferences.KEY_GITHUB_USERNAME, OSMTracker.Preferences.VAL_GITHUB_USERNAME) ?: OSMTracker.Preferences.VAL_GITHUB_USERNAME
		val repo = preferences.getString(OSMTracker.Preferences.KEY_REPOSITORY_NAME, OSMTracker.Preferences.VAL_REPOSITORY_NAME) ?: OSMTracker.Preferences.VAL_REPOSITORY_NAME
		val branch = preferences.getString(OSMTracker.Preferences.KEY_BRANCH_NAME, OSMTracker.Preferences.VAL_BRANCH_NAME) ?: OSMTracker.Preferences.VAL_BRANCH_NAME
		return arrayOf(username, repo, branch)
	}
}


