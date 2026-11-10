package com.lagradost.cloudstream3.lab

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.lagradost.cloudstream3.plugins.PluginData
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.plugins.PLUGIN_VERSION_NOT_SET
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Installed-extension management without any account, media or player adapters. */
class InstalledPluginsFragment : Fragment() {
    private lateinit var list: LinearLayout
    private val importPlugin = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        val context = requireContext().applicationContext
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val folder = File(context.getExternalFilesDir(null), "plugins").apply { mkdirs() }
                    val file = File.createTempFile("local-", ".cs3", folder)
                    try {
                        val stream = requireNotNull(context.contentResolver.openInputStream(uri)) { "Cannot open extension" }
                        stream.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
                        check(PluginManager.loadPlugin(context, file, PluginData(
                            file.nameWithoutExtension, null, false, file.absolutePath, PLUGIN_VERSION_NOT_SET
                        ))) { "Unable to load extension. Only compatible CS3 provider/extractor extensions are supported." }
                    } catch (error: Throwable) {
                        file.delete()
                        throw error
                    }
                }
                if (isAdded) refresh()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (isAdded) AlertDialog.Builder(requireContext()).setMessage(error.message).setPositiveButton("OK", null).show()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        list = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        return ScrollView(requireContext()).apply { addView(list) }
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        list.removeAllViews()
        fun text(value: String) { list.addView(TextView(requireContext()).apply { text = value; textSize = 16f }) }
        list.addView(Button(requireContext()).apply {
            text = "Back to Extensions"
            setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        })
        text("Installed extensions")
        list.addView(Button(requireContext()).apply {
            text = "Import local .cs3 / .zip extension"
            setOnClickListener { importPlugin.launch(arrayOf("*/*")) }
        })
        val installed = (PluginManager.getPluginsOnline() + PluginManager.getPluginsLocal()).distinctBy { it.filePath }
        if (installed.isEmpty()) text("No extensions installed. Add a repository in Extensions or import a local extension.")
        for (plugin in installed.sortedBy { it.internalName }) {
            text("\n${plugin.internalName}\nVersion ${plugin.version} · ${if (plugin.isOnline) "Repository" else "Local"}")
            list.addView(Button(requireContext()).apply {
                text = "Remove ${plugin.internalName}"
                setOnClickListener {
                    AlertDialog.Builder(requireContext()).setTitle("Remove extension?")
                        .setMessage(plugin.internalName).setNegativeButton("Cancel", null)
                        .setPositiveButton("Remove") { _, _ ->
                            viewLifecycleOwner.lifecycleScope.launch {
                                val removed = withContext(Dispatchers.IO) { PluginManager.deletePlugin(File(plugin.filePath)) }
                                if (removed) refresh() else AlertDialog.Builder(requireContext())
                                    .setMessage("Could not delete extension").setPositiveButton("OK", null).show()
                            }
                        }.show()
                }
            })
        }
    }
}
