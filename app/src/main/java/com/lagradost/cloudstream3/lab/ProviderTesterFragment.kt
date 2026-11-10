package com.lagradost.cloudstream3.lab

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.lab.ManualProviderRunner.Method

class ProviderTesterFragment : Fragment() {
    private val model: ProviderTesterViewModel by viewModels()
    private var providers = emptyList<MainAPI>()
    private lateinit var provider: Spinner
    private lateinit var method: Spinner
    private lateinit var value: EditText
    private lateinit var page: EditText
    private lateinit var pageName: EditText
    private lateinit var horizontal: CheckBox
    private lateinit var casting: CheckBox
    private lateinit var preset: Button
    private lateinit var status: TextView
    private var saved: Bundle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        saved = savedInstanceState
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val ctx = requireContext()
        val padding = (16 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        fun text(text: String, size: Float = 15f) = TextView(ctx).apply {
            this.text = text; textSize = size
            setPadding(0, padding / 2, 0, padding / 2)
            content.addView(this)
        }
        fun button(label: String, action: () -> Unit) = Button(ctx).apply {
            text = label; setOnClickListener { action() }; content.addView(this)
        }
        fun field(hint: String, numeric: Boolean = false) = EditText(ctx).apply {
            this.hint = hint
            inputType = if (numeric) InputType.TYPE_CLASS_NUMBER else
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            minLines = 1; maxLines = 6
            content.addView(this)
        }
        text("Extension Lab", 26f)
        text("Manual provider API tester", 20f)
        text("Install extensions in the Extensions tab, then refresh providers here. This page never plays media or runs automatic tests.")
        status = text("")
        text("Provider")
        provider = Spinner(ctx).also { content.addView(it) }
        button("Refresh downloaded providers") { refreshProviders() }
        text("Method")
        method = Spinner(ctx).also {
            it.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, Method.entries.map { m -> m.title })
            content.addView(it)
        }
        preset = button("Use provider main-page preset") {
            val api = providers.getOrNull(provider.selectedItemPosition) ?: return@button
            val entries = api.mainPage
            AlertDialog.Builder(ctx).setTitle("Main page request")
                .setItems(entries.map { it.name.ifBlank { "Default" } }.toTypedArray()) { _, index ->
                    val entry = entries[index]
                    pageName.setText(entry.name); value.setText(entry.data); horizontal.isChecked = entry.horizontalImages
                }.setNegativeButton("Cancel", null).show()
        }
        value = field("Input")
        page = field("Page (positive integer)", true).apply { setText("1") }
        pageName = field("Main-page request name (may be empty)")
        horizontal = CheckBox(ctx).apply { text = "Horizontal images"; content.addView(this) }
        casting = CheckBox(ctx).apply { text = "isCasting"; content.addView(this) }
        method.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { updateFields() }
        }
        val run = button("Run selected API call") {
            val api = providers.getOrNull(provider.selectedItemPosition)
            if (api == null) {
                Toast.makeText(ctx, "No loaded providers. Download an extension, then refresh.", Toast.LENGTH_LONG).show()
                return@button
            }
            val selected = Method.entries[method.selectedItemPosition]
            val needsPage = selected == Method.MAIN_PAGE || selected == Method.PAGED_SEARCH
            val number = if (needsPage) page.text.toString().toIntOrNull() else 1
            if (number == null || number < 1) {
                page.error = "Enter a positive page number"; return@button
            }
            if (selected != Method.MAIN_PAGE && value.text.isBlank()) {
                value.error = "Required input"; return@button
            }
            model.run(api, ManualProviderRunner.Request(selected, value.text.toString(), number,
                pageName.text.toString(), horizontal.isChecked, casting.isChecked))
        }
        val cancel = button("Cancel call") { model.cancel() }
        button("Copy full response") {
            val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            try {
                clipboard.setPrimaryClip(ClipData.newPlainText("Provider response", model.output.value.orEmpty()))
                Toast.makeText(ctx, "Response copied", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(ctx, "Response is too large for the clipboard", Toast.LENGTH_LONG).show()
            }
        }
        text("Response / error", 20f)
        val response = text("").apply {
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 13f
            setTextIsSelectable(true)
        }
        // Observe after onViewCreated, when viewLifecycleOwner exists.
        content.tag = Triple(run, cancel, response)
        saved?.let {
            value.setText(it.getString("value", "")); page.setText(it.getString("page", "1"))
            pageName.setText(it.getString("pageName", "")); horizontal.isChecked = it.getBoolean("horizontal")
            casting.isChecked = it.getBoolean("casting"); method.setSelection(it.getInt("method"))
        }
        updateFields()
        return ScrollView(ctx).apply { addView(content) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        @Suppress("UNCHECKED_CAST")
        val controls = (view as ScrollView).getChildAt(0).tag as Triple<Button, Button, TextView>
        model.output.observe(viewLifecycleOwner) { result ->
            controls.third.text = if (result.length > 200_000) result.take(200_000) +
                "\n\n[Display truncated; Copy full response includes the complete result.]" else result
        }
        model.running.observe(viewLifecycleOwner) { running ->
            controls.first.isEnabled = !running
            controls.second.isEnabled = running
            provider.isEnabled = !running; method.isEnabled = !running
        }
    }

    override fun onResume() {
        super.onResume()
        refreshProviders()
    }

    private fun refreshProviders() {
        val selectedName = providers.getOrNull(provider.selectedItemPosition)?.name ?: saved?.getString("provider")
        providers = APIHolder.apis.toList().sortedWith(compareBy({ it.name }, { it.lang }, { it.mainUrl }))
        provider.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item,
            providers.map { "${it.name} (${it.lang}) — ${it.mainUrl}" }.ifEmpty { listOf("No providers installed") })
        providers.indexOfFirst { it.name == selectedName }.takeIf { it >= 0 }?.let { provider.setSelection(it) }
        val host = activity as? ExtensionLabActivity
        status.text = when {
            host?.pluginsLoading == true -> "Extensions are loading. Tap Refresh when loading finishes."
            host?.pluginLoadError != null -> "Some extensions could not load:\n${host.pluginLoadError}"
            providers.isEmpty() -> "No providers loaded. Download a provider extension in Extensions first."
            else -> "${providers.size} providers loaded. Select one below."
        }
    }

    private fun updateFields() {
        val selected = Method.entries[method.selectedItemPosition.coerceAtLeast(0)]
        val home = selected == Method.MAIN_PAGE
        page.isVisible = home || selected == Method.PAGED_SEARCH
        pageName.isVisible = home; horizontal.isVisible = home; preset.isVisible = home
        casting.isVisible = selected == Method.LOAD_LINKS
        value.hint = when (selected) {
            Method.MAIN_PAGE -> "MainPageRequest.data (use a preset or enter exact data)"
            Method.LOAD -> "Content URL passed to load(url)"
            Method.LOAD_LINKS -> "Exact episode/movie data returned by load (not necessarily a URL)"
            else -> "Search query"
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (!::value.isInitialized) return
        outState.putString("value", value.text.toString()); outState.putString("page", page.text.toString())
        outState.putString("pageName", pageName.text.toString()); outState.putBoolean("horizontal", horizontal.isChecked)
        outState.putBoolean("casting", casting.isChecked); outState.putInt("method", method.selectedItemPosition)
        outState.putString("provider", providers.getOrNull(provider.selectedItemPosition)?.name)
    }
}
