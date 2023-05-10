package com.lagradost.cloudstream3.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.fasterxml.jackson.annotation.JsonProperty
import com.hippo.unifile.UniFile
import com.lagradost.cloudstream3.APIHolder.allProviders
import com.lagradost.cloudstream3.AcraApplication
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.normalSafeApiCall
import com.lagradost.cloudstream3.network.initClient
import com.lagradost.cloudstream3.ui.EasterEggMonke
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.getPref
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setPaddingBottom
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setUpToolbar
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialog
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showDialog
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showMultiDialog
import com.lagradost.cloudstream3.utils.SubtitleHelper
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import com.lagradost.cloudstream3.utils.USER_PROVIDER_API
import com.lagradost.cloudstream3.utils.VideoDownloadManager
import com.lagradost.cloudstream3.utils.VideoDownloadManager.getBasePath
import kotlinx.android.synthetic.main.add_remove_sites.*
import kotlinx.android.synthetic.main.add_site_input.*
import java.io.File

fun getCurrentLocale(context: Context): String {
    val res = context.resources
    // Change locale settings in the app.
    // val dm = res.displayMetrics
    val conf = res.configuration
    return conf?.locale?.toString() ?: "en"
}

// idk, if you find a way of automating this it would be great
// https://www.iemoji.com/view/emoji/1794/flags/antarctica
// Emoji Character Encoding Data --> C/C++/Java Src
// https://en.wikipedia.org/wiki/List_of_ISO_639-1_codes leave blank for auto
val appLanguages = arrayListOf(
    /* begin language list */
    Triple("", "العربية", "ar"),
    Triple("", "ars", "ars"),
    Triple("", "български", "bg"),
    Triple("", "বাংলা", "bn"),
    Triple("\uD83C\uDDE7\uD83C\uDDF7", "português brasileiro", "bp"),
    Triple("", "čeština", "cs"),
    Triple("", "Deutsch", "de"),
    Triple("", "Ελληνικά", "el"),
    Triple("", "English", "en"),
    Triple("", "Esperanto", "eo"),
    Triple("", "español", "es"),
    Triple("", "فارسی", "fa"),
    Triple("", "français", "fr"),
    Triple("", "हिन्दी", "hi"),
    Triple("", "hrvatski", "hr"),
    Triple("", "magyar", "hu"),
    Triple("\uD83C\uDDEE\uD83C\uDDE9", "Bahasa Indonesia", "in"),
    Triple("", "italiano", "it"),
    Triple("\uD83C\uDDEE\uD83C\uDDF1", "עברית", "iw"),
    Triple("", "日本語 (にほんご)", "ja"),
    Triple("", "ಕನ್ನಡ", "kn"),
    Triple("", "한국어", "ko"),
    Triple("", "latviešu valoda", "lv"),
    Triple("", "македонски", "mk"),
    Triple("", "മലയാളം", "ml"),
    Triple("", "bahasa Melayu", "ms"),
    Triple("", "Nederlands", "nl"),
    Triple("", "norsk nynorsk", "nn"),
    Triple("", "norsk bokmål", "no"),
    Triple("", "ଓଡ଼ିଆ", "or"),
    Triple("", "polski", "pl"),
    Triple("\uD83C\uDDF5\uD83C\uDDF9", "português", "pt"),
    Triple("\uD83E\uDD8D", "mmmm... monke", "qt"),
    Triple("", "română", "ro"),
    Triple("", "русский", "ru"),
    Triple("", "slovenčina", "sk"),
    Triple("", "Soomaaliga", "so"),
    Triple("", "svenska", "sv"),
    Triple("", "தமிழ்", "ta"),
    Triple("", "Tagalog", "tl"),
    Triple("", "Türkçe", "tr"),
    Triple("", "українська", "uk"),
    Triple("", "اردو", "ur"),
    Triple("", "Tiếng Việt", "vi"),
    Triple("", "中文", "zh"),
    Triple("\uD83C\uDDF9\uD83C\uDDFC", "正體中文(臺灣)", "zh-rTW"),
/* end language list */
).sortedBy { it.second.lowercase() } //ye, we go alphabetical, so ppl don't put their lang on top

class SettingsGeneral : PreferenceFragmentCompat() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpToolbar(R.string.category_general)
        setPaddingBottom()
    }

    data class CustomSite(
        @JsonProperty("parentJavaClass") // javaClass.simpleName
        val parentJavaClass: String,
        @JsonProperty("name")
        val name: String,
        @JsonProperty("url")
        val url: String,
        @JsonProperty("lang")
        val lang: String,
    )

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

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        hideKeyboard()
        setPreferencesFromResource(R.xml.settins_general, rootKey)
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(requireContext())

        fun getCurrent(): MutableList<CustomSite> {
            return getKey<Array<CustomSite>>(USER_PROVIDER_API)?.toMutableList()
                ?: mutableListOf()
        }

        getPref(R.string.locale_key)?.setOnPreferenceClickListener { pref ->
            val tempLangs = appLanguages.toMutableList()
            val current = getCurrentLocale(pref.context)
            val languageCodes = tempLangs.map { (_, _, iso) -> iso }
            val languageNames = tempLangs.map { (emoji, name, iso) ->
                val flag = emoji.ifBlank { SubtitleHelper.getFlagFromIso(iso) ?: "ERROR" }
                "$flag $name"
            }
            val index = languageCodes.indexOf(current)

            activity?.showDialog(
                languageNames, index, getString(R.string.app_language), true, { }
            ) { languageIndex ->
                try {
                    val code = languageCodes[languageIndex]
                    CommonActivity.setLocale(activity, code)
                    settingsManager.edit().putString(getString(R.string.locale_key), code).apply()
                    activity?.recreate()
                } catch (e: Exception) {
                    logError(e)
                }
            }
            return@setOnPreferenceClickListener true
        }


        fun showAdd() {
            val providers = allProviders.distinctBy { it.javaClass }.sortedBy { it.name }
            activity?.showDialog(
                providers.map { "${it.name} (${it.mainUrl})" },
                -1,
                context?.getString(R.string.add_site_pref) ?: return,
                true,
                {}) { selection ->
                val provider = providers.getOrNull(selection) ?: return@showDialog

                val builder =
                    AlertDialog.Builder(context ?: return@showDialog, R.style.AlertDialogCustom)
                        .setView(R.layout.add_site_input)

                val dialog = builder.create()
                dialog.show()

                dialog.text2?.text = provider.name
                dialog.apply_btt?.setOnClickListener {
                    val name = dialog.site_name_input?.text?.toString()
                    val url = dialog.site_url_input?.text?.toString()
                    val lang = dialog.site_lang_input?.text?.toString()
                    val realLang = if (lang.isNullOrBlank()) provider.lang else lang
                    if (url.isNullOrBlank() || name.isNullOrBlank() || realLang.length != 2) {
                        showToast(activity, R.string.error_invalid_data, Toast.LENGTH_SHORT)
                        return@setOnClickListener
                    }

                    val current = getCurrent()
                    val newSite = CustomSite(provider.javaClass.simpleName, name, url, realLang)
                    current.add(newSite)
                    setKey(USER_PROVIDER_API, current.toTypedArray())

                    dialog.dismissSafe(activity)
                }
                dialog.cancel_btt?.setOnClickListener {
                    dialog.dismissSafe(activity)
                }
            }
        }

        fun showDelete() {
            val current = getCurrent()

            activity?.showMultiDialog(
                current.map { it.name },
                listOf(),
                context?.getString(R.string.remove_site_pref) ?: return,
                {}) { indexes ->
                current.removeAll(indexes.map { current[it] })
                setKey(USER_PROVIDER_API, current.toTypedArray())
            }
        }

        fun showAddOrDelete() {
            val builder =
                AlertDialog.Builder(context ?: return, R.style.AlertDialogCustom)
                    .setView(R.layout.add_remove_sites)

            val dialog = builder.create()
            dialog.show()

            dialog.add_site?.setOnClickListener {
                showAdd()
                dialog.dismissSafe(activity)
            }
            dialog.remove_site?.setOnClickListener {
                showDelete()
                dialog.dismissSafe(activity)
            }
        }

        getPref(R.string.override_site_key)?.setOnPreferenceClickListener { _ ->

            if (getCurrent().isEmpty()) {
                showAdd()
            } else {
                showAddOrDelete()
            }

            return@setOnPreferenceClickListener true
        }

        getPref(R.string.legal_notice_key)?.setOnPreferenceClickListener {
            val builder: AlertDialog.Builder =
                AlertDialog.Builder(it.context, R.style.AlertDialogCustom)
            builder.setTitle(R.string.legal_notice)
            builder.setMessage(R.string.legal_notice_text)
            builder.show()
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
        fun getDownloadDirs(): List<String> {
            return normalSafeApiCall {
                val defaultDir = VideoDownloadManager.getDownloadDir()?.filePath

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

        settingsManager.edit().putBoolean(getString(R.string.jsdelivr_proxy_key), getKey(getString(R.string.jsdelivr_proxy_key), false) ?: false).apply()
        getPref(R.string.jsdelivr_proxy_key)?.setOnPreferenceChangeListener { _, newValue ->
            setKey(getString(R.string.jsdelivr_proxy_key), newValue)
            return@setOnPreferenceChangeListener true
        }

        getPref(R.string.download_path_key)?.setOnPreferenceClickListener {
            val dirs = getDownloadDirs()

            val currentDir =
                settingsManager.getString(getString(R.string.download_path_pref), null)
                    ?: VideoDownloadManager.getDownloadDir().toString()

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

        try {
            SettingsFragment.beneneCount =
                settingsManager.getInt(getString(R.string.benene_count), 0)
            getPref(R.string.benene_count)?.let { pref ->
                pref.summary =
                    if (SettingsFragment.beneneCount <= 0) getString(R.string.benene_count_text_none) else getString(
                        R.string.benene_count_text
                    ).format(
                        SettingsFragment.beneneCount
                    )

                pref.setOnPreferenceClickListener {
                    try {
                        SettingsFragment.beneneCount++
                        if (SettingsFragment.beneneCount%20 == 0) {
                            val intent = Intent(context, EasterEggMonke::class.java)
                            startActivity(intent)
                        }
                        settingsManager.edit().putInt(
                            getString(R.string.benene_count),
                            SettingsFragment.beneneCount
                        )
                            .apply()
                        it.summary =
                            getString(R.string.benene_count_text).format(SettingsFragment.beneneCount)
                    } catch (e: Exception) {
                        logError(e)
                    }

                    return@setOnPreferenceClickListener true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
