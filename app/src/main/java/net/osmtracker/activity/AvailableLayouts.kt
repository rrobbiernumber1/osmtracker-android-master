package net.osmtracker.activity

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import net.osmtracker.OSMTracker
import net.osmtracker.R
import net.osmtracker.layout.DownloadCustomLayoutTask
import net.osmtracker.layout.GetStringResponseTask
import net.osmtracker.layout.URLValidatorTask
import net.osmtracker.util.CustomLayoutsUtils
import net.osmtracker.util.URLCreator
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.util.ArrayList
import java.util.HashMap
import java.util.Locale

class AvailableLayouts : Activity() {

	private val TMP_SHARED_PREFERENCES_FILE = "net.osmtracker.tmpspfile"
	private var isDefChecked = false
	private lateinit var sharedPrefs: SharedPreferences
	private lateinit var editor: SharedPreferences.Editor
	private lateinit var etxGithubUsername: EditText
	private lateinit var etxRepositoryName: EditText
	private lateinit var etxBranchName: EditText
	private lateinit var defaultServerCheckBox: CheckBox
	private lateinit var customServerCheckBox: CheckBox
	private var checkBoxPressed = false

    companion object {
        const val ISO_CHARACTER_LENGTH = 2
        fun isNetworkAvailable(context: Context): Boolean {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetworkInfo: NetworkInfo? = connectivityManager.activeNetworkInfo
            return activeNetworkInfo != null && activeNetworkInfo.isConnected
        }
    }

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
		editor = sharedPrefs.edit()
		title = resources.getString(R.string.prefs_ui_available_layout)
		if (isNetworkAvailable(this)) {
			validateDefaultOptions()
		} else {
			Toast.makeText(applicationContext, resources.getString(R.string.available_layouts_connection_error), Toast.LENGTH_LONG).show()
			finish()
		}
	}

	@SuppressLint("StaticFieldLeak")
	fun validateDefaultOptions() {
		val usernameGitHub = sharedPrefs.getString(OSMTracker.Preferences.KEY_GITHUB_USERNAME, OSMTracker.Preferences.VAL_GITHUB_USERNAME)
		val repositoryName = sharedPrefs.getString(OSMTracker.Preferences.KEY_REPOSITORY_NAME, OSMTracker.Preferences.VAL_REPOSITORY_NAME)
		val branchName = sharedPrefs.getString(OSMTracker.Preferences.KEY_BRANCH_NAME, OSMTracker.Preferences.VAL_BRANCH_NAME)
		val repositoryDefaultOptions = arrayOf(usernameGitHub, repositoryName, branchName)
		object : URLValidatorTask() {
			override fun onPostExecute(result: Boolean) {
				if (result) {
					retrieveAvailableLayouts()
				} else {
					Toast.makeText(applicationContext, resources.getString(R.string.available_layouts_response_null_exception), Toast.LENGTH_LONG).show()
					finish()
				}
			}
		}.execute(*repositoryDefaultOptions)
	}

    @SuppressLint("StaticFieldLeak")
    fun retrieveAvailableLayouts() {
		val waitingMessage = resources.getString(R.string.available_layouts_connecting_message)
		title = resources.getString(R.string.prefs_ui_available_layout) + waitingMessage
		val url = URLCreator.createMetadataDirUrl(this)
		object : GetStringResponseTask() {
			override fun onPostExecute(response: String?) {
				if (response == null) {
					Toast.makeText(applicationContext, resources.getString(R.string.available_layouts_response_null_exception), Toast.LENGTH_LONG).show()
					finish()
				} else {
					setContentView(R.layout.available_layouts)
					setAvailableLayouts(parseResponse(response))
					title = resources.getString(R.string.prefs_ui_available_layout)
				}
			}
		}.execute(url)
	}

    

	fun setAvailableLayouts(options: List<String>?) {
		val rootLayout = findViewById<LinearLayout>(R.id.root_layout)
		val AT_START = 0
		val listener = ClickListener()
		Log.e("#", options.toString())
		for (option in options!!) {
			val layoutButton = Button(this)
			layoutButton.height = 150
			layoutButton.text = CustomLayoutsUtils.convertFileName(option)
			layoutButton.textSize = 16f
			layoutButton.setTextColor(Color.WHITE)
			layoutButton.isSingleLine = false
			val layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
			layoutParams.setMargins(50, 10, 50, 10)
			layoutButton.layoutParams = layoutParams
			layoutButton.setPadding(40, 30, 40, 30)
			layoutButton.setOnClickListener(listener)
			rootLayout.addView(layoutButton, AT_START)
		}
	}

	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		menuInflater.inflate(R.menu.github_repository_settings_menu, menu)
		return super.onCreateOptionsMenu(menu)
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		if (item.itemId == R.id.github_config) {
			val inflater = baseContext.getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
			val repositoryConfigWindow = inflater.inflate(R.layout.github_repository_settings, null)
			etxGithubUsername = repositoryConfigWindow.findViewById(R.id.github_username)
			etxRepositoryName = repositoryConfigWindow.findViewById(R.id.repository_name)
			etxBranchName = repositoryConfigWindow.findViewById(R.id.branch_name)
			defaultServerCheckBox = repositoryConfigWindow.findViewById(R.id.default_server)
			customServerCheckBox = repositoryConfigWindow.findViewById(R.id.custom_server)
			val tmpSharedPref = applicationContext.getSharedPreferences(TMP_SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE)
			val isCallBack = tmpSharedPref.getBoolean("isCallBack", false)
			if (!isCallBack) {
				if (sharedPrefs.getBoolean("defCheck", true)) {
					toggleRepositoryOptions(true)
				} else {
					toggleRepositoryOptions(false)
				}
			} else {
				toggleRepositoryOptions(false)
				etxGithubUsername.setText(tmpSharedPref.getString(OSMTracker.Preferences.KEY_GITHUB_USERNAME, ""))
				etxRepositoryName.setText(tmpSharedPref.getString(OSMTracker.Preferences.KEY_REPOSITORY_NAME, ""))
				etxBranchName.setText(tmpSharedPref.getString(OSMTracker.Preferences.KEY_BRANCH_NAME, ""))
			}
			checkBoxPressed = false
			defaultServerCheckBox.setOnClickListener {
				checkBoxPressed = true
				toggleRepositoryOptions(true)
				isDefChecked = true
				editor.putBoolean("defCheck", isDefChecked)
				editor.commit()
			}
			customServerCheckBox.setOnClickListener {
				checkBoxPressed = true
				toggleRepositoryOptions(false)
				isDefChecked = false
				editor.putBoolean("defCheck", isDefChecked)
				editor.commit()
			}
			AlertDialog.Builder(this)
				.setTitle(resources.getString(R.string.prefs_ui_github_repository_settings))
				.setView(repositoryConfigWindow)
				.setPositiveButton(resources.getString(R.string.menu_save)) { _: DialogInterface?, _: Int ->
					val repositoryCustomOptions = arrayOf(
						etxGithubUsername.text.toString(),
						etxRepositoryName.text.toString(),
						etxBranchName.text.toString()
					)
					object : URLValidatorTask() {
						override fun onPostExecute(result: Boolean) {
							if (result) {
								val message = resources.getString(R.string.github_repository_settings_valid_server)
								Log.i("TOAST", message)
								Toast.makeText(this@AvailableLayouts, message, Toast.LENGTH_SHORT).show()
								editor.putString(OSMTracker.Preferences.KEY_GITHUB_USERNAME, repositoryCustomOptions[0])
								editor.putString(OSMTracker.Preferences.KEY_REPOSITORY_NAME, repositoryCustomOptions[1])
								editor.putString(OSMTracker.Preferences.KEY_BRANCH_NAME, repositoryCustomOptions[2])
								editor.commit()
								val tmpEditor = tmpSharedPref.edit()
								tmpEditor.putBoolean("isCallBack", false).commit()
								retrieveAvailableLayouts()
							} else {
								val message = resources.getString(R.string.github_repository_settings_invalid_server)
								Log.e("TOAST", message)
								Toast.makeText(this@AvailableLayouts, message, Toast.LENGTH_SHORT).show()
								tmpSharedPref.edit().putString(OSMTracker.Preferences.KEY_GITHUB_USERNAME, repositoryCustomOptions[0]).commit()
								tmpSharedPref.edit().putString(OSMTracker.Preferences.KEY_REPOSITORY_NAME, repositoryCustomOptions[1]).commit()
								tmpSharedPref.edit().putString(OSMTracker.Preferences.KEY_BRANCH_NAME, repositoryCustomOptions[2]).commit()
								tmpSharedPref.edit().putBoolean("isCallBack", true).commit()
								onOptionsItemSelected(item)
							}
						}
					}.execute(*repositoryCustomOptions)
				}
				.setNegativeButton(resources.getString(R.string.menu_cancel)) { dialog: DialogInterface, _: Int ->
					tmpSharedPref.edit().putBoolean("isCallBack", false).commit()
					if (checkBoxPressed) {
						if (!isDefChecked) {
							toggleRepositoryOptions(true)
							isDefChecked = true
							editor.putBoolean("defCheck", isDefChecked)
							editor.commit()
						} else {
							toggleRepositoryOptions(false)
							isDefChecked = false
							editor.putBoolean("defCheck", isDefChecked)
							editor.commit()
						}
					}
					dialog.cancel()
				}
				.setCancelable(true)
				.create().show()
		}
		return super.onOptionsItemSelected(item)
	}

	private fun toggleRepositoryOptions(status: Boolean) {
		customServerCheckBox.isChecked = !status
		customServerCheckBox.isEnabled = status
		defaultServerCheckBox.isChecked = status
		defaultServerCheckBox.isEnabled = !status
		etxGithubUsername.isEnabled = !status
		etxBranchName.isEnabled = !status
		etxRepositoryName.isEnabled = !status
		if (status) {
			etxGithubUsername.setText(OSMTracker.Preferences.VAL_GITHUB_USERNAME)
			etxRepositoryName.setText(OSMTracker.Preferences.VAL_REPOSITORY_NAME)
			etxBranchName.setText(OSMTracker.Preferences.VAL_BRANCH_NAME)
		} else {
			etxGithubUsername.setText(sharedPrefs.getString(OSMTracker.Preferences.KEY_GITHUB_USERNAME, ""))
			etxRepositoryName.setText(sharedPrefs.getString(OSMTracker.Preferences.KEY_REPOSITORY_NAME, OSMTracker.Preferences.VAL_REPOSITORY_NAME))
			etxBranchName.setText(sharedPrefs.getString(OSMTracker.Preferences.KEY_BRANCH_NAME, OSMTracker.Preferences.VAL_BRANCH_NAME))
		}
	}

    private fun parseResponse(response: String): List<String> {
        val options: MutableList<String> = ArrayList()
		try {
			val jsonArray = JSONArray(response)
			for (i in 0 until jsonArray.length()) {
				val `object`: JSONObject = jsonArray.getJSONObject(i)
				options.add(`object`.getString("name"))
			}
        } catch (e: JSONException) {
            e.printStackTrace(); return emptyList<String>()
		}
        return options.toList()
	}

	private fun getLanguagesFor(xmlFile: String): HashMap<String, String> {
		val languages = HashMap<String, String>()
		try {
			val parser = XmlPullParserFactory.newInstance().newPullParser()
			parser.setInput(ByteArrayInputStream(xmlFile.toByteArray()), "UTF-8")
			var eventType = parser.eventType
			while (eventType != XmlPullParser.END_DOCUMENT) {
				if (parser.eventType == XmlPullParser.START_TAG && parser.name == "option") {
					val name = parser.getAttributeValue(null, "name")
					val iso = parser.getAttributeValue(null, "iso")
					languages[name] = iso
				}
				eventType = parser.next()
			}
		} catch (e: Exception) {
			e.printStackTrace()
		}
		return languages
	}

	private fun getDescriptionFor(xmlFile: String, localeLanguage: String): String? {
		var description: String? = null
		try {
			val parser = XmlPullParserFactory.newInstance().newPullParser()
			parser.setInput(ByteArrayInputStream(xmlFile.toByteArray()), "UTF-8")
			var eventType = parser.eventType
			while (eventType != XmlPullParser.END_DOCUMENT && description == null) {
				if (eventType == XmlPullParser.START_TAG && parser.name == "option") {
					val iso = parser.getAttributeValue("", "iso")
					if (iso != null && iso == localeLanguage) {
						parser.next()
						description = parser.text
					}
				}
				eventType = parser.next()
			}
		} catch (e: Exception) {
			Log.e("#", "Error parsing metadata files: ${e}")
		}
		return description
	}

	private fun showDescriptionDialog(layoutName: String, description: String, iso: String) {
		val b = AlertDialog.Builder(this)
		b.setTitle(layoutName)
		b.setNegativeButton(resources.getString(R.string.menu_cancel), null)
		b.setPositiveButton(resources.getString(R.string.available_layouts_description_dialog_positive_confirmation), DownloadListener(layoutName, iso, this))
		b.setMessage(description)
		b.create().show()
	}

    private fun showLanguageSelectionDialog(languages: HashMap<String, String>, xmlFile: String, layoutName: String) {
        val keys = languages.keys
        val options: Array<CharSequence> = keys.map { it as CharSequence }.toTypedArray()
		Toast.makeText(this, resources.getString(R.string.available_layouts_not_available_language), Toast.LENGTH_LONG).show()
		val b = AlertDialog.Builder(this)
		b.setTitle(resources.getString(R.string.available_layouts_language_dialog_title))
        b.setItems(options) { _: DialogInterface?, i: Int ->
            val key = options[i].toString()
            val desc = getDescriptionFor(xmlFile, languages[key]!!)
            showDescriptionDialog(layoutName, desc!!, languages[key]!!)
		}
		b.create().show()
	}

	private inner class ClickListener : View.OnClickListener {
		override fun onClick(view: View) {
			val layoutName = "" + (view as TextView).text
			val url = URLCreator.createMetadataFileURL(view.context, layoutName)
			val dialog = ProgressDialog(view.context)
			dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER)
			dialog.setMessage(resources.getString(R.string.available_layouts_checking_language_dialog))
			dialog.show()
            object : GetStringResponseTask() {
                override fun onPostExecute(response: String?) {
					dialog.dismiss()
                    val xmlFile = response ?: return
					val localLang = Locale.getDefault().language
					val description = getDescriptionFor(xmlFile, localLang)
					if (description != null) {
						showDescriptionDialog(layoutName, description, localLang)
					} else {
						val languages = getLanguagesFor(xmlFile)
						Log.e("#", languages.toString())
						showLanguageSelectionDialog(languages, xmlFile, layoutName)
					}
				}
			}.execute(url)
		}
	}

	private inner class DownloadListener(
		private val layoutName: String,
		private val iso: String,
		private val context: Context
    ) : DialogInterface.OnClickListener {
		override fun onClick(dialogInterface: DialogInterface, i: Int) {
			val info = arrayOf(layoutName, iso)
			val dialog = ProgressDialog(context)
			dialog.setMessage(resources.getString(R.string.available_layouts_downloading_dialog))
			dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER)
			dialog.show()
			object : DownloadCustomLayoutTask(context) {
				override fun onPostExecute(status: Boolean) {
					var message = ""
					if (status) {
						message = resources.getString(R.string.available_layouts_successful_download)
						Log.i("TOAST", message)
					} else {
						message = resources.getString(R.string.available_layouts_unsuccessful_download)
						Log.e("TOAST", message)
					}
					Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
					dialog.dismiss()
				}
			}.execute(*info)
		}
	}
}


