package com.lagradost.cloudstream3.ui.settings


import android.app.UiModeManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.hippo.unifile.UniFile
import com.lagradost.cloudstream3.APIHolder.apis
import com.lagradost.cloudstream3.APIHolder.getApiDubstatusSettings
import com.lagradost.cloudstream3.APIHolder.getApiProviderLangSettings
import com.lagradost.cloudstream3.AcraApplication
import com.lagradost.cloudstream3.AcraApplication.Companion.removeKey
import com.lagradost.cloudstream3.CommonActivity.setLocale
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.normalSafeApiCall
import com.lagradost.cloudstream3.network.initClient
import com.lagradost.cloudstream3.syncproviders.AccountManager
import com.lagradost.cloudstream3.syncproviders.OAuth2API
import com.lagradost.cloudstream3.syncproviders.OAuth2API.Companion.aniListApi
import com.lagradost.cloudstream3.syncproviders.OAuth2API.Companion.malApi
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.subtitles.ChromecastSubtitlesFragment
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment
import com.lagradost.cloudstream3.utils.BackupUtils.backup
import com.lagradost.cloudstream3.utils.BackupUtils.restorePrompt
import com.lagradost.cloudstream3.utils.HOMEPAGE_API
import com.lagradost.cloudstream3.utils.InAppUpdater.Companion.runAutoUpdate
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialog
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showDialog
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showMultiDialog
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showNginxTextInputDialog
import com.lagradost.cloudstream3.utils.SubtitleHelper
import com.lagradost.cloudstream3.utils.SubtitleHelper.getFlagFromIso
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import com.lagradost.cloudstream3.utils.UIHelper.setImage
import com.lagradost.cloudstream3.utils.VideoDownloadManager
import com.lagradost.cloudstream3.utils.VideoDownloadManager.getBasePath
import com.lagradost.cloudstream3.utils.VideoDownloadManager.getDownloadDir
import kotlinx.android.synthetic.main.logcat.*
import okhttp3.internal.closeQuietly
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import kotlin.concurrent.thread


class SettingsFragment : PreferenceFragmentCompat() {
    companion object {
        private fun Context.getLayoutInt(): Int {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
            return settingsManager.getInt(this.getString(R.string.app_layout_key), -1)
        }

        fun Context.isTvSettings(): Boolean {
            var value = getLayoutInt()
            if (value == -1) {
                value = if (isAutoTv()) 1 else 0
            }
            return value == 1 || value == 2
        }

        fun Context.isTrueTvSettings(): Boolean {
            var value = getLayoutInt()
            if (value == -1) {
                value = if (isAutoTv()) 1 else 0
            }
            return value == 1
        }

        fun Context.isEmulatorSettings(): Boolean {
            return getLayoutInt() == 2
        }

        private fun Context.isAutoTv(): Boolean {
            val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager?
            return uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        }

        const val accountEnabled = true
    }

    private var beneneCount = 0

    // Open file picker
    private val pathPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            // It lies, it can be null if file manager quits.
            if (uri == null) return@registerForActivityResult
            val context = context ?: AcraApplication.context ?: return@registerForActivityResult
            // RW perms for the path
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION

            context.contentResolver.takePersistableUriPermission(uri, flags)

            val file = UniFile.fromUri(context, uri)
            println("Selected URI path: $uri - Full path: ${file.filePath}")

            // Stores the real URI using download_path_key
            // Important that the URI is stored instead of filepath due to permissions.
            PreferenceManager.getDefaultSharedPreferences(context)
                .edit().putString(getString(R.string.download_path_key), uri.toString()).apply()

            // From URI -> File path
            // File path here is purely for cosmetic purposes in settings
            (file.filePath ?: uri.toString()).let {
                PreferenceManager.getDefaultSharedPreferences(context)
                    .edit().putString(getString(R.string.download_path_pref), it).apply()
            }
        }

    // idk, if you find a way of automating this it would be great
    // https://www.iemoji.com/view/emoji/1794/flags/antarctica
    // Emoji Character Encoding Data --> C/C++/Java Src
    // https://en.wikipedia.org/wiki/List_of_ISO_639-1_codes leave blank for auto
    private val languages = arrayListOf(
        Triple("", "Spanish", "es"),
        Triple("", "English", "en"),
        Triple("", "Viet Nam", "vi"),
        Triple("", "Dutch", "nl"),
        Triple("", "French", "fr"),
        Triple("", "Greek", "el"),
        Triple("", "Swedish", "sv"),
        Triple("", "Tagalog", "tl"),
        Triple("", "Polish", "pl"),
        Triple("", "Hindi", "hi"),
        Triple("", "Malayalam", "ml"),
        Triple("", "Norsk", "no"),
        Triple("", "German", "de"),
        Triple("", "Arabic", "ar"),
        Triple("", "Turkish", "tr"),
        Triple("", "Macedonian", "mk"),
        Triple("", "Portuguese (Brazil)", "pt"),
        Triple("", "Romanian", "ro"),
        Triple("", "Italian", "it"),
        Triple("", "Chinese", "zh"),
    ).sortedBy { it.second } //ye, we go alphabetical, so ppl don't put their lang on top

    private fun showAccountSwitch(context: Context, api: AccountManager) {
        val accounts = api.getAccounts() ?: return

        val builder =
            AlertDialog.Builder(context, R.style.AlertDialogCustom).setView(R.layout.account_switch)
        val dialog = builder.show()

        dialog.findViewById<TextView>(R.id.account_add)?.setOnClickListener {
            try {
                api.authenticate()
            } catch (e: Exception) {
                logError(e)
            }
        }

        val ogIndex = api.accountIndex

        val items = ArrayList<OAuth2API.LoginInfo>()

        for (index in accounts) {
            api.accountIndex = index
            val accountInfo = api.loginInfo()
            if (accountInfo != null) {
                items.add(accountInfo)
            }
        }
        api.accountIndex = ogIndex
        val adapter = AccountAdapter(items, R.layout.account_single) {
            dialog?.dismissSafe(activity)
            api.changeAccount(it.card.accountIndex)
        }
        val list = dialog.findViewById<RecyclerView>(R.id.account_list)
        list?.adapter = adapter
    }

    private fun showLoginInfo(api: AccountManager, info: OAuth2API.LoginInfo) {
        val builder =
            AlertDialog.Builder(context ?: return, R.style.AlertDialogCustom)
                .setView(R.layout.account_managment)
        val dialog = builder.show()

        dialog.findViewById<ImageView>(R.id.account_profile_picture)?.setImage(info.profilePicture)
        dialog.findViewById<TextView>(R.id.account_logout)?.setOnClickListener {
            api.logOut()
            dialog.dismissSafe(activity)
        }

        (info.name ?: context?.getString(R.string.no_data))?.let {
            dialog.findViewById<TextView>(R.id.account_name)?.text = it
        }
        dialog.findViewById<TextView>(R.id.account_site)?.text = api.name
        dialog.findViewById<TextView>(R.id.account_switch_account)?.setOnClickListener {
            dialog.dismissSafe(activity)
            showAccountSwitch(it.context, api)
        }
    }

    private fun getPref(id: Int): Preference? {
        return try {
            findPreference(getString(id))
        } catch (e: Exception) {
            logError(e)
            null
        }
    }

    private fun getFolderSize(dir: File): Long {
        var size: Long = 0
        dir.listFiles()?.let {
            for (file in it) {
                size += if (file.isFile) {
                    // System.out.println(file.getName() + " " + file.length());
                    file.length()
                } else getFolderSize(file)
            }
        }

        return size
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        hideKeyboard()
        setPreferencesFromResource(R.xml.settings, rootKey)
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(requireContext())

        getPref(R.string.video_buffer_length_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.video_buffer_length_names)
            val prefValues = resources.getIntArray(R.array.video_buffer_length_values)

            val currentPrefSize =
                settingsManager.getInt(getString(R.string.video_buffer_length_key), 0)

            activity?.showDialog(
                prefNames.toList(),
                prefValues.indexOf(currentPrefSize),
                getString(R.string.video_buffer_length_settings),
                true,
                {}) {
                settingsManager.edit()
                    .putInt(getString(R.string.video_buffer_length_key), prefValues[it])
                    .apply()
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.video_buffer_size_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.video_buffer_size_names)
            val prefValues = resources.getIntArray(R.array.video_buffer_size_values)

            val currentPrefSize =
                settingsManager.getInt(getString(R.string.video_buffer_size_key), 0)

            activity?.showDialog(
                prefNames.toList(),
                prefValues.indexOf(currentPrefSize),
                getString(R.string.video_buffer_size_settings),
                true,
                {}) {
                settingsManager.edit()
                    .putInt(getString(R.string.video_buffer_size_key), prefValues[it])
                    .apply()
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.video_buffer_clear_key)?.let { pref ->
            val cacheDir = context?.cacheDir ?: return@let

            fun updateSummery() {
                try {
                    pref.summary =
                        getString(R.string.mb_format).format(getFolderSize(cacheDir) / (1024L * 1024L))
                } catch (e: Exception) {
                    logError(e)
                }
            }

            updateSummery()

            pref.setOnPreferenceClickListener {
                try {
                    cacheDir.deleteRecursively()
                    updateSummery()
                } catch (e: Exception) {
                    logError(e)
                }
                return@setOnPreferenceClickListener true
            }
        }

        getPref(R.string.video_buffer_disk_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.video_buffer_size_names)
            val prefValues = resources.getIntArray(R.array.video_buffer_size_values)

            val currentPrefSize =
                settingsManager.getInt(getString(R.string.video_buffer_disk_key), 0)

            activity?.showDialog(
                prefNames.toList(),
                prefValues.indexOf(currentPrefSize),
                getString(R.string.video_buffer_disk_settings),
                true,
                {}) {
                settingsManager.edit()
                    .putInt(getString(R.string.video_buffer_disk_key), prefValues[it])
                    .apply()
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.subtitle_settings_key)?.setOnPreferenceClickListener {
            SubtitlesFragment.push(activity, false)
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.subtitle_settings_chromecast_key)?.setOnPreferenceClickListener {
            ChromecastSubtitlesFragment.push(activity, false)
            return@setOnPreferenceClickListener true
        }

        val syncApis =
            listOf(Pair(R.string.mal_key, malApi), Pair(R.string.anilist_key, aniListApi))
        for ((key, api) in syncApis) {
            getPref(key)?.apply {
                isVisible = accountEnabled
                title =
                    getString(R.string.login_format).format(api.name, getString(R.string.account))
                setOnPreferenceClickListener { _ ->
                    val info = api.loginInfo()
                    if (info != null) {
                        showLoginInfo(api, info)
                    } else {
                        try {
                            api.authenticate()
                        } catch (e: Exception) {
                            logError(e)
                        }
                    }
                    return@setOnPreferenceClickListener true
                }
            }
        }

        getPref(R.string.legal_notice_key)?.setOnPreferenceClickListener {
            val builder: AlertDialog.Builder = AlertDialog.Builder(it.context)
            builder.setTitle(R.string.legal_notice)
            builder.setMessage(R.string.legal_notice_text)
            builder.show()
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.display_sub_key)?.setOnPreferenceClickListener {
            activity?.getApiDubstatusSettings()?.let { current ->
                val dublist = DubStatus.values()
                val names = dublist.map { it.name }

                val currentList = ArrayList<Int>()
                for (i in current) {
                    currentList.add(dublist.indexOf(i))
                }

                activity?.showMultiDialog(
                    names,
                    currentList,
                    getString(R.string.display_subbed_dubbed_settings),
                    {}) { selectedList ->
                    APIRepository.dubStatusActive = selectedList.map { dublist[it] }.toHashSet()

                    settingsManager.edit().putStringSet(
                        this.getString(R.string.display_sub_key),
                        selectedList.map { names[it] }.toMutableSet()
                    ).apply()
                }
            }

            return@setOnPreferenceClickListener true
        }

        getPref(R.string.provider_lang_key)?.setOnPreferenceClickListener {
            activity?.getApiProviderLangSettings()?.let { current ->
                val allLangs = HashSet<String>()
                for (api in apis) {
                    allLangs.add(api.lang)
                }

                val currentList = ArrayList<Int>()
                for (i in current) {
                    currentList.add(allLangs.indexOf(i))
                }

                val names = allLangs.map {
                    val emoji = getFlagFromIso(it)
                    val name = SubtitleHelper.fromTwoLettersToLanguage(it)
                    val fullName = "$emoji $name"
                    Pair(it, fullName)
                }

                activity?.showMultiDialog(
                    names.map { it.second },
                    currentList,
                    getString(R.string.provider_lang_settings),
                    {}) { selectedList ->
                    settingsManager.edit().putStringSet(
                        this.getString(R.string.provider_lang_key),
                        selectedList.map { names[it].first }.toMutableSet()
                    ).apply()
                    //APIRepository.providersActive = it.context.getApiSettings()
                }
            }

            return@setOnPreferenceClickListener true
        }

        fun getDownloadDirs(): List<String> {
            return normalSafeApiCall {
                val defaultDir = getDownloadDir()?.filePath

                // app_name_download_path = Cloudstream and does not change depending on release.
                // DOES NOT WORK ON SCOPED STORAGE.
                val secondaryDir =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) null else Environment.getExternalStorageDirectory().absolutePath +
                            File.separator + resources.getString(R.string.app_name_download_path)
                val first = listOf(defaultDir, secondaryDir)
                (try {
                    val currentDir = context?.getBasePath()?.let { it.first?.filePath ?: it.second }

                    (first +
                            requireContext().getExternalFilesDirs("").mapNotNull { it.path } +
                            currentDir)
                } catch (e: Exception) {
                    first
                }).filterNotNull().distinct()
            } ?: emptyList()
        }

        getPref(R.string.download_path_key)?.setOnPreferenceClickListener {
            val dirs = getDownloadDirs()

            val currentDir =
                settingsManager.getString(getString(R.string.download_path_pref), null)
                    ?: getDownloadDir().toString()

            activity?.showBottomDialog(
                dirs + listOf("Custom"),
                dirs.indexOf(currentDir),
                getString(R.string.download_path_pref),
                true,
                {}) {
                // Last = custom
                if (it == dirs.size) {
                    try {
                        pathPicker.launch(Uri.EMPTY)
                    } catch (e: Exception) {
                        logError(e)
                    }
                } else {
                    // Sets both visual and actual paths.
                    // key = used path
                    // pref = visual path
                    settingsManager.edit()
                        .putString(getString(R.string.download_path_key), dirs[it]).apply()
                    settingsManager.edit()
                        .putString(getString(R.string.download_path_pref), dirs[it]).apply()
                }
            }
            return@setOnPreferenceClickListener true
        }

	getPref(R.string.nginx_url_key)?.setOnPreferenceClickListener {

            activity?.showNginxTextInputDialog(
                settingsManager.getString(getString(R.string.nginx_url_pref), "Nginx server url").toString(),
                settingsManager.getString(getString(R.string.nginx_url_key), "").toString(),  // key: the actual you use rn
                android.text.InputType.TYPE_TEXT_VARIATION_URI,  // uri
                {}) {
                settingsManager.edit()
                    .putString(getString(R.string.nginx_url_key), it).apply()  // change the stored url in nginx_url_key to it
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.nginx_credentials)?.setOnPreferenceClickListener {

            activity?.showNginxTextInputDialog(
                settingsManager.getString(getString(R.string.nginx_credentials_title), "Nginx Credentials").toString(),
                settingsManager.getString(getString(R.string.nginx_credentials), "").toString(),  // key: the actual you use rn
                android.text.InputType.TYPE_TEXT_VARIATION_URI,
                {}) {
                settingsManager.edit()
                    .putString(getString(R.string.nginx_credentials), it).apply()  // change the stored url in nginx_url_key to it
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.prefer_media_type_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.media_type_pref)
            val prefValues = resources.getIntArray(R.array.media_type_pref_values)

            val currentPrefMedia =
                settingsManager.getInt(getString(R.string.prefer_media_type_key), 0)

            activity?.showBottomDialog(
                prefNames.toList(),
                prefValues.indexOf(currentPrefMedia),
                getString(R.string.preferred_media_settings),
                true,
                {}) {
                settingsManager.edit()
                    .putInt(getString(R.string.prefer_media_type_key), prefValues[it])
                    .apply()

                removeKey(HOMEPAGE_API)
                (context ?: AcraApplication.context)?.let { ctx -> app.initClient(ctx) }
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.show_logcat_key)?.setOnPreferenceClickListener { pref ->
            val builder =
                AlertDialog.Builder(pref.context, R.style.AlertDialogCustom)
                    .setView(R.layout.logcat)

            val dialog = builder.create()
            dialog.show()
            val log = StringBuilder()
            try {
                //https://developer.android.com/studio/command-line/logcat
                val process = Runtime.getRuntime().exec("logcat -d")
                val bufferedReader = BufferedReader(
                    InputStreamReader(process.inputStream)
                )

                var line: String?
                while (bufferedReader.readLine().also { line = it } != null) {
                    log.append(line)
                }
            } catch (e: Exception) {
                logError(e) // kinda ironic
            }

            val text = log.toString()
            dialog.text1?.text = text

            dialog.copy_btt?.setOnClickListener {
                val serviceClipboard =
                    (activity?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?)
                        ?: return@setOnClickListener
                val clip = ClipData.newPlainText("logcat", text)
                serviceClipboard.setPrimaryClip(clip)
                dialog.dismissSafe(activity)
            }
            dialog.clear_btt?.setOnClickListener {
                Runtime.getRuntime().exec("logcat -c")
                dialog.dismissSafe(activity)
            }
            dialog.save_btt?.setOnClickListener {
                var fileStream: OutputStream? = null
                try {
                    fileStream =
                        VideoDownloadManager.setupStream(
                            it.context,
                            "logcat",
                            null,
                            "txt",
                            false
                        ).fileStream
                    fileStream?.writer()?.write(text)
                } catch (e: Exception) {
                    logError(e)
                } finally {
                    fileStream?.closeQuietly()
                    dialog.dismissSafe(activity)
                }
            }
            dialog.close_btt?.setOnClickListener {
                dialog.dismissSafe(activity)
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.app_layout_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.app_layout)
            val prefValues = resources.getIntArray(R.array.app_layout_values)

            val currentLayout =
                settingsManager.getInt(getString(R.string.app_layout_key), -1)

            activity?.showBottomDialog(
                prefNames.toList(),
                prefValues.indexOf(currentLayout),
                getString(R.string.app_layout),
                true,
                {}) {
                try {
                    settingsManager.edit()
                        .putInt(getString(R.string.app_layout_key), prefValues[it])
                        .apply()
                    activity?.recreate()
                } catch (e: Exception) {
                    logError(e)
                }
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.backup_key)?.setOnPreferenceClickListener {
            activity?.backup()
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.restore_key)?.setOnPreferenceClickListener {
            activity?.restorePrompt()
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.primary_color_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.themes_overlay_names)
            val prefValues = resources.getStringArray(R.array.themes_overlay_names_values)

            val currentLayout =
                settingsManager.getString(getString(R.string.primary_color_key), prefValues.first())

            activity?.showDialog(
                prefNames.toList(),
                prefValues.indexOf(currentLayout),
                getString(R.string.primary_color_settings),
                true,
                {}) {
                try {
                    settingsManager.edit()
                        .putString(getString(R.string.primary_color_key), prefValues[it])
                        .apply()
                    activity?.recreate()
                } catch (e: Exception) {
                    logError(e)
                }
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.app_theme_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.themes_names)
            val prefValues = resources.getStringArray(R.array.themes_names_values)

            val currentLayout =
                settingsManager.getString(getString(R.string.app_theme_key), prefValues.first())

            activity?.showBottomDialog(
                prefNames.toList(),
                prefValues.indexOf(currentLayout),
                getString(R.string.app_theme_settings),
                true,
                {}) {
                try {
                    settingsManager.edit()
                        .putString(getString(R.string.app_theme_key), prefValues[it])
                        .apply()
                    activity?.recreate()
                } catch (e: Exception) {
                    logError(e)
                }
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.quality_pref_key)?.setOnPreferenceClickListener {
            val prefValues = Qualities.values().map { it.value }.reversed().toMutableList()
            prefValues.remove(Qualities.Unknown.value)

            val prefNames = prefValues.map { Qualities.getStringByInt(it) }

            val currentQuality =
                settingsManager.getInt(
                    getString(R.string.quality_pref_key),
                    Qualities.values().last().value
                )

            activity?.showBottomDialog(
                prefNames.toList(),
                prefValues.indexOf(currentQuality),
                getString(R.string.watch_quality_pref),
                true,
                {}) {
                settingsManager.edit().putInt(getString(R.string.quality_pref_key), prefValues[it])
                    .apply()
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.prefer_limit_title_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.limit_title_pref_names)
            val prefValues = resources.getIntArray(R.array.limit_title_pref_values)
            val current = settingsManager.getInt(getString(R.string.prefer_limit_title_key), 0)

            activity?.showBottomDialog(
                prefNames.toList(),
                prefValues.indexOf(current),
                getString(R.string.limit_title),
                true,
                {}) {
                settingsManager.edit()
                    .putInt(getString(R.string.prefer_limit_title_key), prefValues[it])
                    .apply()
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.dns_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.dns_pref)
            val prefValues = resources.getIntArray(R.array.dns_pref_values)

            val currentDns =
                settingsManager.getInt(getString(R.string.dns_pref), 0)

            activity?.showBottomDialog(
                prefNames.toList(),
                prefValues.indexOf(currentDns),
                getString(R.string.dns_pref),
                true,
                {}) {
                settingsManager.edit().putInt(getString(R.string.dns_pref), prefValues[it]).apply()
                (context ?: AcraApplication.context)?.let { ctx -> app.initClient(ctx) }
            }
            return@setOnPreferenceClickListener true
        }

        try {
            beneneCount = settingsManager.getInt(getString(R.string.benene_count), 0)
            getPref(R.string.benene_count)?.let { pref ->
                pref.summary =
                    if (beneneCount <= 0) getString(R.string.benene_count_text_none) else getString(
                        R.string.benene_count_text
                    ).format(
                        beneneCount
                    )

                pref.setOnPreferenceClickListener {
                    try {
                        beneneCount++
                        settingsManager.edit().putInt(getString(R.string.benene_count), beneneCount)
                            .apply()
                        it.summary = getString(R.string.benene_count_text).format(beneneCount)
                    } catch (e: Exception) {
                        logError(e)
                    }

                    return@setOnPreferenceClickListener true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        getPref(R.string.manual_check_update_key)?.setOnPreferenceClickListener {
            thread {
                if (!requireActivity().runAutoUpdate(false)) {
                    activity?.runOnUiThread {
                        showToast(activity, R.string.no_update_found, Toast.LENGTH_SHORT)
                    }
                }
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.locale_key)?.setOnPreferenceClickListener { pref ->
            val tempLangs = languages.toMutableList()
            if (beneneCount > 100) {
                tempLangs.add(Triple("\uD83E\uDD8D", "mmmm... monke", "mo"))
            }
            val current = getCurrentLocale()
            val languageCodes = tempLangs.map { it.third }
            val languageNames = tempLangs.map { (emoji, name, iso) ->
                val flag = emoji.ifBlank { getFlagFromIso(iso) ?: "ERROR" }

                "$flag $name"
            }
            val index = languageCodes.indexOf(current)

            activity?.showDialog(
                languageNames, index, getString(R.string.app_language), true, { }
            ) { languageIndex ->
                try {
                    val code = languageCodes[languageIndex]
                    setLocale(activity, code)
                    settingsManager.edit().putString(getString(R.string.locale_key), code).apply()
                    activity?.recreate()
                } catch (e: Exception) {
                    logError(e)
                }
            }
            return@setOnPreferenceClickListener true
        }
    }

    private fun getCurrentLocale(): String {
        val res = requireContext().resources
// Change locale settings in the app.
        // val dm = res.displayMetrics
        val conf = res.configuration
        return conf?.locale?.language ?: "en"
    }
}
