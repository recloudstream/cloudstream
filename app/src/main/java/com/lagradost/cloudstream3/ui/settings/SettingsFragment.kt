package com.lagradost.cloudstream3.ui.settings

import android.os.Bundle
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.MainActivity.Companion.showToast
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.normalSafeApiCall
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.InAppUpdater.Companion.runAutoUpdate
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.util.*
import kotlin.Exception
import kotlin.concurrent.thread

class SettingsFragment : PreferenceFragmentCompat() {
    var count = 0
    private var scoreboard: List<ScoreManager.DreamloEntry>? = null

    private var usernameUUID: String? = null

    var ongoingJob: Job? = null

    private fun saveAfterTime() {
        ongoingJob?.cancel()
        ongoingJob = main {
            delay(10000) // dont ddos the scoreboard
            saveAndUpload()
        }
    }

    private fun saveAndUpload() {
        if (ScoreManager.privateCode.isNullOrBlank()) return
        try {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(context)
            val uuid = usernameUUID
            if (uuid != null) {
                settingsManager.edit()
                    .putString(getString(R.string.benene_count_uuid), uuid)
                    .putInt(getString(R.string.benene_count), count)
                    .apply()
                thread {
                    normalSafeApiCall {
                        ScoreManager.addScore(uuid, count)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onPause() {
        saveAndUpload()
        super.onPause()
    }

    override fun onDestroy() {
        saveAndUpload()
        super.onDestroy()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        hideKeyboard()
        setPreferencesFromResource(R.xml.settings, rootKey)
        val updatePrefrence = findPreference<Preference>(getString(R.string.manual_check_update_key))!!

        val benenePref = findPreference<Preference>(getString(R.string.benene_count))!!

        try {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(context)

            count = settingsManager.getInt(getString(R.string.benene_count), 0)
            usernameUUID =
                settingsManager.getString(getString(R.string.benene_count_uuid), UUID.randomUUID().toString())
            if (count > 20) {
                if (!ScoreManager.privateCode.isNullOrBlank()) {
                    thread {
                        scoreboard = normalSafeApiCall { ScoreManager.getScore() }
                    }
                }
            }
            benenePref.summary =
                if (count <= 0) getString(R.string.benene_count_text_none) else getString(R.string.benene_count_text).format(
                    count
                )
            benenePref.setOnPreferenceClickListener {
                try {
                    count++
                    settingsManager.edit().putInt(getString(R.string.benene_count), count).apply()
                    var add = ""
                    val localScoreBoard = scoreboard
                    if (localScoreBoard != null) {
                        for ((index, score) in localScoreBoard.withIndex()) {
                            if (count > (score.score.toIntOrNull() ?: 0)) {
                                add = " (${index + 1}/${localScoreBoard.size})"
                                break
                            }
                        }
                    }
                    it.summary = getString(R.string.benene_count_text).format(count) + add
                    saveAfterTime()
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                return@setOnPreferenceClickListener true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        updatePrefrence.setOnPreferenceClickListener {
            thread {
                if (!requireActivity().runAutoUpdate(false)) {
                    activity?.runOnUiThread {
                        showToast(activity, "No Update Found", Toast.LENGTH_SHORT)
                    }
                }
            }
            return@setOnPreferenceClickListener true
        }
    }

    override fun onPreferenceTreeClick(preference: Preference?): Boolean {
        if (preference != null) {
            if (preference.key == "subtitle_settings_key") {
                SubtitlesFragment.push(activity, false)
            }
        }
        return super.onPreferenceTreeClick(preference)
    }
}