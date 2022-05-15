package com.lagradost.cloudstream3.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.hippo.unifile.UniFile
import com.lagradost.cloudstream3.AcraApplication
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.normalSafeApiCall
import com.lagradost.cloudstream3.network.initClient
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.getFolderSize
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.getPref
import com.lagradost.cloudstream3.ui.subtitles.ChromecastSubtitlesFragment
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialog
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showDialog
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import com.lagradost.cloudstream3.utils.VideoDownloadManager
import com.lagradost.cloudstream3.utils.VideoDownloadManager.getBasePath
import java.io.File

class SettingsPlayer : PreferenceFragmentCompat() {
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
        setPreferencesFromResource(R.xml.settings_player, rootKey)
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

        /*(getPref(R.string.double_tap_seek_time_key) as? SeekBarPreference?)?.let {

        }*/

        getPref(R.string.prefer_limit_title_rez_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.limit_title_rez_pref_names)
            val prefValues = resources.getIntArray(R.array.limit_title_rez_pref_values)
            val current = settingsManager.getInt(getString(R.string.prefer_limit_title_rez_key), 3)

            activity?.showBottomDialog(
                prefNames.toList(),
                prefValues.indexOf(current),
                getString(R.string.limit_title_rez),
                true,
                {}) {
                settingsManager.edit()
                    .putInt(getString(R.string.prefer_limit_title_rez_key), prefValues[it])
                    .apply()
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

        getPref(R.string.subtitle_settings_key)?.setOnPreferenceClickListener {
            SubtitlesFragment.push(activity, false)
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.subtitle_settings_chromecast_key)?.setOnPreferenceClickListener {
            ChromecastSubtitlesFragment.push(activity, false)
            return@setOnPreferenceClickListener true
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

    }
}