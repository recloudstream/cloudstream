package com.lagradost.cloudstream3.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.TransactionTooLargeException
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.getPref
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setPaddingBottom
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setUpToolbar
import com.lagradost.cloudstream3.utils.BackupUtils.backup
import com.lagradost.cloudstream3.utils.BackupUtils.restorePrompt
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.InAppUpdater.Companion.runAutoUpdate
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialog
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import com.lagradost.cloudstream3.utils.VideoDownloadManager
import kotlinx.android.synthetic.main.logcat.*
import okhttp3.internal.closeQuietly
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream

class SettingsUpdates : PreferenceFragmentCompat() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpToolbar(R.string.category_updates)
        setPaddingBottom()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        hideKeyboard()
        setPreferencesFromResource(R.xml.settings_updates, rootKey)
        //val settingsManager = PreferenceManager.getDefaultSharedPreferences(requireContext())

        getPref(R.string.backup_key)?.setOnPreferenceClickListener {
            activity?.backup()
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.redo_setup_key)?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.navigation_setup_language)
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.restore_key)?.setOnPreferenceClickListener {
            activity?.restorePrompt()
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
                    log.append("${line}\n")
                }
            } catch (e: Exception) {
                logError(e) // kinda ironic
            }

            val text = log.toString()
            dialog.text1?.text = text

            dialog.copy_btt?.setOnClickListener {
                // Can crash on too much text
                try {
                    val serviceClipboard =
                        (activity?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?)
                            ?: return@setOnClickListener
                    val clip = ClipData.newPlainText("logcat", text)
                    serviceClipboard.setPrimaryClip(clip)
                    dialog.dismissSafe(activity)
                } catch (e: TransactionTooLargeException) {
                    showToast(activity, R.string.clipboard_too_large)
                }
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

        getPref(R.string.apk_installer_key)?.setOnPreferenceClickListener {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(it.context)

            val prefNames = resources.getStringArray(R.array.apk_installer_pref)
            val prefValues = resources.getIntArray(R.array.apk_installer_values)

            val currentInstaller =
                settingsManager.getInt(getString(R.string.apk_installer_key), 0)

            activity?.showBottomDialog(
                prefNames.toList(),
                prefValues.indexOf(currentInstaller),
                getString(R.string.apk_installer_settings),
                true,
                {}) {
                try {
                    settingsManager.edit()
                        .putInt(getString(R.string.apk_installer_key), prefValues[it])
                        .apply()
                } catch (e: Exception) {
                    logError(e)
                }
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.manual_check_update_key)?.setOnPreferenceClickListener {
            ioSafe {
                if (activity?.runAutoUpdate(false) == false) {
                    activity?.runOnUiThread {
                        showToast(
                            activity,
                            R.string.no_update_found,
                            Toast.LENGTH_SHORT
                        )
                    }
                }
            }
            return@setOnPreferenceClickListener true
        }
    }
}
