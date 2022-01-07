package com.lagradost.cloudstream3.ui.settings


import android.app.UiModeManager
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
import com.lagradost.cloudstream3.APIHolder.getApiSettings
import com.lagradost.cloudstream3.APIHolder.restrictedApis
import com.lagradost.cloudstream3.AcraApplication
import com.lagradost.cloudstream3.AcraApplication.Companion.removeKey
import com.lagradost.cloudstream3.CommonActivity.setLocale
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.syncproviders.AccountManager
import com.lagradost.cloudstream3.syncproviders.OAuth2API
import com.lagradost.cloudstream3.syncproviders.OAuth2API.Companion.aniListApi
import com.lagradost.cloudstream3.syncproviders.OAuth2API.Companion.malApi
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment
import com.lagradost.cloudstream3.utils.HOMEPAGE_API
import com.lagradost.cloudstream3.utils.InAppUpdater.Companion.runAutoUpdate
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialog
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showDialog
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showMultiDialog
import com.lagradost.cloudstream3.utils.SubtitleHelper
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import com.lagradost.cloudstream3.utils.UIHelper.setImage
import com.lagradost.cloudstream3.utils.VideoDownloadManager.getBasePath
import com.lagradost.cloudstream3.utils.VideoDownloadManager.getDownloadDir
import java.io.File
import kotlin.concurrent.thread


class SettingsFragment : PreferenceFragmentCompat() {
    companion object {
        fun Context.isTvSettings(): Boolean {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
            var value = settingsManager.getInt(this.getString(R.string.app_layout_key), -1)
            if (value == -1) {
                value = if (isAutoTv()) 1 else 0
            }
            return value == 1
        }

        private fun Context.isAutoTv(): Boolean {
            val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager?
            return uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        }

        private const val accountEnabled = false
    }

    private var beneneCount = 0

    // Open file picker
    private val pathPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
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
    private val languages = arrayListOf(
        Triple("\uD83C\uDDEA\uD83C\uDDF8", "Spanish", "es"),
        Triple("\uD83C\uDDEC\uD83C\uDDE7", "English", "en"),
        Triple("\uD83C\uDDFB\uD83C\uDDF3", "Viet Nam", "vi"),
        Triple("\uD83C\uDDF3\uD83C\uDDF1", "Dutch", "nl"),
        Triple("\uD83C\uDDEB\uD83C\uDDF7", "French", "fr"),
        Triple("\uD83C\uDDEC\uD83C\uDDF7", "Greek", "gr"),
        Triple("\uD83C\uDDF8\uD83C\uDDEA", "Swedish", "sv"),
        Triple("\uD83C\uDDF5\uD83C\uDDED", "Tagalog", "tl"),
        Triple("\uD83C\uDDF5\uD83C\uDDF1", "Polish", "pl"),
        Triple("\uD83C\uDDEE\uD83C\uDDF3", "Hindi", "hi"),
        Triple("\uD83C\uDDEE\uD83C\uDDF3", "Malayalam", "ml"),
        Triple("\uD83C\uDDF3\uD83C\uDDF4", "Norsk", "no"),
        Triple("\ud83c\udde9\ud83c\uddea", "German", "de"),
        Triple("\ud83c\udde6\ud83c\uddf7", "Arabic", "ar"),// originally flag_lb \ud83c\uddf1\ud83c\udde7
        Triple("\ud83c\uddf9\ud83c\uddf7", "Turkish", "tr"),
        Triple("\ud83c\uddf2\ud83c\uddf0", "Macedonian", "mk"),
        Triple("\ud83c\udde7\ud83c\uddf7", "Portuguese (Brazil)", "pt"),
        Triple("\ud83c\uddf7\ud83c\uddf4", "Romanian", "ro"),
    ).sortedBy { it.second } //ye, we go alphabetical, so ppl don't put their lang on top

    private fun showAccountSwitch(context: Context, api: AccountManager) {
        val accounts = api.getAccounts() ?: return

        val builder =
            AlertDialog.Builder(context, R.style.AlertDialogCustom).setView(R.layout.account_switch)
        val dialog = builder.show()

        dialog.findViewById<TextView>(R.id.account_add)?.setOnClickListener {
            api.authenticate()
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
            AlertDialog.Builder(context ?: return, R.style.AlertDialogCustom).setView(R.layout.account_managment)
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

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        hideKeyboard()
        setPreferencesFromResource(R.xml.settings, rootKey)

        val updatePreference = findPreference<Preference>(getString(R.string.manual_check_update_key))!!
        val localePreference = findPreference<Preference>(getString(R.string.locale_key))!!
        val benenePreference = findPreference<Preference>(getString(R.string.benene_count))!!
        val watchQualityPreference = findPreference<Preference>(getString(R.string.quality_pref_key))!!
        val dnsPreference = findPreference<Preference>(getString(R.string.dns_key))!!
        val legalPreference = findPreference<Preference>(getString(R.string.legal_notice_key))!!
        val subdubPreference = findPreference<Preference>(getString(R.string.display_sub_key))!!
        val providerLangPreference = findPreference<Preference>(getString(R.string.provider_lang_key))!!
        val downloadPathPreference = findPreference<Preference>(getString(R.string.download_path_key))!!
        val allLayoutPreference = findPreference<Preference>(getString(R.string.app_layout_key))!!
        val colorPrimaryPreference = findPreference<Preference>(getString(R.string.primary_color_key))!!
        val preferedMediaTypePreference = findPreference<Preference>(getString(R.string.prefer_media_type_key))!!
        val appThemePreference = findPreference<Preference>(getString(R.string.app_theme_key))!!
        val subPreference = findPreference<Preference>(getString(R.string.subtitle_settings_key))!!

        subPreference.setOnPreferenceClickListener {
            SubtitlesFragment.push(activity, false)
            return@setOnPreferenceClickListener true
        }

        val syncApis = listOf(Pair(R.string.mal_key, malApi), Pair(R.string.anilist_key, aniListApi))
        for (sync in syncApis) {
            findPreference<Preference>(getString(sync.first))?.apply {
                isVisible = accountEnabled
                val api = sync.second
                title = getString(R.string.login_format).format(api.name, getString(R.string.account))
                setOnPreferenceClickListener { pref ->
                    pref.context?.let { ctx ->
                        val info = api.loginInfo()
                        if (info != null) {
                            showLoginInfo(api, info)
                        } else {
                            api.authenticate()
                        }
                    }

                    return@setOnPreferenceClickListener true
                }
            }
        }

        legalPreference.setOnPreferenceClickListener {
            val builder: AlertDialog.Builder = AlertDialog.Builder(it.context)
            builder.setTitle(R.string.legal_notice)
            builder.setMessage(R.string.legal_notice_text)
            builder.show()
            return@setOnPreferenceClickListener true
        }

        subdubPreference.setOnPreferenceClickListener {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(context)

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

        providerLangPreference.setOnPreferenceClickListener {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(context)

            activity?.getApiProviderLangSettings()?.let { current ->
                val allLangs = HashSet<String>()
                for (api in apis) {
                    allLangs.add(api.lang)
                }
                for (api in restrictedApis) {
                    allLangs.add(api.lang)
                }

                val currentList = ArrayList<Int>()
                for (i in current) {
                    currentList.add(allLangs.indexOf(i))
                }

                val names = allLangs.mapNotNull {
                    val fullName = SubtitleHelper.fromTwoLettersToLanguage(it)
                    if (fullName.isNullOrEmpty()) {
                        return@mapNotNull null
                    }

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
                    APIRepository.providersActive = it.context.getApiSettings()
                }
            }

            return@setOnPreferenceClickListener true
        }

        fun getDownloadDirs(): List<String> {
            val defaultDir = getDownloadDir()?.filePath

            // app_name_download_path = Cloudstream and does not change depending on release.
            // DOES NOT WORK ON SCOPED STORAGE.
            val secondaryDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) null else Environment.getExternalStorageDirectory().absolutePath +
                    File.separator + resources.getString(R.string.app_name_download_path)
            val first = listOf(defaultDir, secondaryDir)
            return (try {
                val currentDir = context?.getBasePath()?.let { it.first?.filePath ?: it.second }

                (first +
                        requireContext().getExternalFilesDirs("").mapNotNull { it.path } +
                        currentDir)
            } catch (e: Exception) {
                first
            }).filterNotNull().distinct()
        }

        downloadPathPreference.setOnPreferenceClickListener {
            val dirs = getDownloadDirs()
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(context)

            val currentDir =
                settingsManager.getString(getString(R.string.download_path_pref), null) ?: getDownloadDir().toString()

            activity?.showBottomDialog(
                dirs + listOf("Custom"),
                dirs.indexOf(currentDir),
                getString(R.string.download_path_pref),
                true,
                {}) {
                // Last = custom
                if (it == dirs.size) {
                    pathPicker.launch(Uri.EMPTY)
                } else {
                    // Sets both visual and actual paths.
                    // key = used path
                    // pref = visual path
                    settingsManager.edit().putString(getString(R.string.download_path_key), dirs[it]).apply()
                    settingsManager.edit().putString(getString(R.string.download_path_pref), dirs[it]).apply()
                }
            }
            return@setOnPreferenceClickListener true
        }

        preferedMediaTypePreference.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.media_type_pref)
            val prefValues = resources.getIntArray(R.array.media_type_pref_values)
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(context)

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

        allLayoutPreference.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.app_layout)
            val prefValues = resources.getIntArray(R.array.app_layout_values)
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(context)

            val currentLayout =
                settingsManager.getInt(getString(R.string.app_layout_key), -1)

            activity?.showBottomDialog(
                prefNames.toList(),
                prefValues.indexOf(currentLayout),
                getString(R.string.app_layout),
                true,
                {}) {
                try {
                    settingsManager.edit().putInt(getString(R.string.app_layout_key), prefValues[it])
                        .apply()
                    activity?.recreate()
                } catch (e: Exception) {
                    logError(e)
                }
            }
            return@setOnPreferenceClickListener true
        }

        colorPrimaryPreference.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.themes_overlay_names)
            val prefValues = resources.getStringArray(R.array.themes_overlay_names_values)
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(context)

            val currentLayout =
                settingsManager.getString(getString(R.string.primary_color_key), prefValues.first())

            activity?.showBottomDialog(
                prefNames.toList(),
                prefValues.indexOf(currentLayout),
                getString(R.string.primary_color_settings),
                true,
                {}) {
                try {
                    settingsManager.edit().putString(getString(R.string.primary_color_key), prefValues[it])
                        .apply()
                    activity?.recreate()
                } catch (e: Exception) {
                    logError(e)
                }
            }
            return@setOnPreferenceClickListener true
        }

        appThemePreference.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.themes_names)
            val prefValues = resources.getStringArray(R.array.themes_names_values)
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(context)

            val currentLayout =
                settingsManager.getString(getString(R.string.app_theme_key), prefValues.first())

            activity?.showBottomDialog(
                prefNames.toList(),
                prefValues.indexOf(currentLayout),
                getString(R.string.app_theme_settings),
                true,
                {}) {
                try {
                    settingsManager.edit().putString(getString(R.string.app_theme_key), prefValues[it])
                        .apply()
                    activity?.recreate()
                } catch (e: Exception) {
                    logError(e)
                }
            }
            return@setOnPreferenceClickListener true
        }

        watchQualityPreference.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.quality_pref)
            val prefValues = resources.getIntArray(R.array.quality_pref_values)
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(context)

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

        dnsPreference.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.dns_pref)
            val prefValues = resources.getIntArray(R.array.dns_pref_values)
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(context)

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
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(context)

            beneneCount = settingsManager.getInt(getString(R.string.benene_count), 0)

            benenePreference.summary =
                if (beneneCount <= 0) getString(R.string.benene_count_text_none) else getString(R.string.benene_count_text).format(
                    beneneCount
                )

            benenePreference.setOnPreferenceClickListener {
                try {
                    beneneCount++
                    settingsManager.edit().putInt(getString(R.string.benene_count), beneneCount).apply()
                    it.summary = getString(R.string.benene_count_text).format(beneneCount)
                } catch (e: Exception) {
                    logError(e)
                }

                return@setOnPreferenceClickListener true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        updatePreference.setOnPreferenceClickListener {
            thread {
                if (!requireActivity().runAutoUpdate(false)) {
                    activity?.runOnUiThread {
                        showToast(activity, R.string.no_update_found, Toast.LENGTH_SHORT)
                    }
                }
            }
            return@setOnPreferenceClickListener true
        }

        localePreference.setOnPreferenceClickListener { pref ->
            val tempLangs = languages.toMutableList()
            if (beneneCount > 100) {
                tempLangs.add(Triple("\uD83E\uDD8D", "mmmm... monke", "mo"))
            }
            val current = getCurrentLocale()
            val languageCodes = tempLangs.map { it.third }
            val languageNames = tempLangs.map { "${it.first}  ${it.second}" }
            val index = languageCodes.indexOf(current)

            activity?.showDialog(
                languageNames, index, getString(R.string.app_language), true, { }
            ) { languageIndex ->
                try {
                    val code = languageCodes[languageIndex]
                    setLocale(activity, code)
                    val settingsManager = PreferenceManager.getDefaultSharedPreferences(pref.context)
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
        val res = context!!.resources
// Change locale settings in the app.
        // val dm = res.displayMetrics
        val conf = res.configuration
        return conf?.locale?.language ?: "en"
    }
}
