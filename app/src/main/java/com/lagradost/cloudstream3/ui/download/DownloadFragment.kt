package com.lagradost.cloudstream3.ui.download

import android.app.Dialog
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.format.Formatter.formatShortFileSize
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentDownloadsBinding
import com.lagradost.cloudstream3.databinding.StreamInputBinding
import com.lagradost.cloudstream3.isMovieType
import com.lagradost.cloudstream3.mvvm.normalSafeApiCall
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.download.DownloadButtonSetup.handleDownloadClick
import com.lagradost.cloudstream3.ui.player.BasicLink
import com.lagradost.cloudstream3.ui.player.GeneratorPlayer
import com.lagradost.cloudstream3.ui.player.LinkGenerator
import com.lagradost.cloudstream3.ui.result.FOCUS_SELF
import com.lagradost.cloudstream3.ui.result.setLinearListLayout
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.AppUtils.loadResult
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.DOWNLOAD_EPISODE_CACHE
import com.lagradost.cloudstream3.utils.DataStore
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbar
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.cloudstream3.utils.UIHelper.setAppBarNoScrollFlagsOnTV
import com.lagradost.cloudstream3.utils.VideoDownloadManager
import java.net.URI

const val DOWNLOAD_NAVIGATE_TO = "downloadpage"

class DownloadFragment : Fragment() {
    private lateinit var downloadsViewModel: DownloadViewModel

    private fun View.setLayoutWidth(weight: Long) {
        val param = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.MATCH_PARENT,
            maxOf((weight / 1000000000f), 0.1f) // 100mb
        )
        this.layoutParams = param
    }

    private fun setList(list: List<VisualDownloadHeaderCached>) {
        main {
            (binding?.downloadList?.adapter as? DownloadAdapter)?.submitList(list)
        }
    }

    override fun onDestroyView() {
        downloadDeleteEventListener?.let {
            VideoDownloadManager.downloadDeleteEvent -= it
        }
        downloadDeleteEventListener = null
        binding = null
        super.onDestroyView()
    }

    private var binding: FragmentDownloadsBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        downloadsViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        val localBinding = FragmentDownloadsBinding.inflate(inflater, container, false)
        binding = localBinding
        return localBinding.root
    }

    private var downloadDeleteEventListener: ((Int) -> Unit)? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        hideKeyboard()
        binding?.downloadStorageAppbar?.setAppBarNoScrollFlagsOnTV()

        observe(downloadsViewModel.noDownloadsText) {
            binding?.textNoDownloads?.text = it
        }
        observe(downloadsViewModel.headerCards) {
            setList(it)
            binding?.downloadLoading?.isVisible = false
        }
        observe(downloadsViewModel.availableBytes) {
            updateStorageInfo(view.context, it, R.string.free_storage, binding?.downloadFreeTxt, binding?.downloadFree)
        }
        observe(downloadsViewModel.usedBytes) {
            updateStorageInfo(view.context, it, R.string.used_storage, binding?.downloadUsedTxt, binding?.downloadUsed)
            binding?.downloadStorageAppbar?.isVisible = it > 0
        }
        observe(downloadsViewModel.downloadBytes) {
            updateStorageInfo(view.context, it, R.string.app_storage, binding?.downloadAppTxt, binding?.downloadApp)
        }

        val adapter = DownloadAdapter(
            { click ->
                handleItemClick(click)
            },
            { downloadClickEvent ->
                handleDownloadClick(downloadClickEvent)
                if (downloadClickEvent.action == DOWNLOAD_ACTION_DELETE_FILE) {
                    setUpDownloadDeleteListener()
                }
            }
        )

        binding?.downloadList?.apply {
            setHasFixedSize(true)
            setItemViewCacheSize(20)
            this.adapter = adapter
            setLinearListLayout(
                isHorizontal = false,
                nextRight = FOCUS_SELF,
                nextUp = FOCUS_SELF,
                nextDown = FOCUS_SELF,
            )
        }

        binding?.downloadStreamButton?.apply {
            isGone = isLayout(TV)
            setOnClickListener { showStreamInputDialog(it.context) }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            binding?.downloadList?.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                handleScroll(scrollY - oldScrollY)
            }
        }
        downloadsViewModel.updateList(requireContext())
        fixPaddingStatusbar(binding?.downloadRoot)
    }

    private fun handleItemClick(click: DownloadHeaderClickEvent) {
        when (click.action) {
            0 -> {
                if (!click.data.type.isMovieType()) {
                    val folder = DataStore.getFolderName(DOWNLOAD_EPISODE_CACHE, click.data.id.toString())
                    activity?.navigate(
                        R.id.action_navigation_downloads_to_navigation_download_child,
                        DownloadChildFragment.newInstance(click.data.name, folder)
                    )
                }
            }
            1 -> {
                (activity as AppCompatActivity?)?.loadResult(click.data.url, click.data.apiName)
            }
        }
    }

    private fun setUpDownloadDeleteListener() {
        downloadDeleteEventListener = { id ->
            val list = (binding?.downloadList?.adapter as? DownloadAdapter)?.currentList
            if (list?.any { it.data.id == id } == true) {
                context?.let { downloadsViewModel.updateList(it) }
            }
        }
        downloadDeleteEventListener?.let { VideoDownloadManager.downloadDeleteEvent += it }
    }

    private fun updateStorageInfo(
        context: Context,
        bytes: Long,
        @StringRes stringRes: Int,
        textView: TextView?,
        view: View?
    ) {
        textView?.text = getString(R.string.storage_size_format).format(getString(stringRes), formatShortFileSize(context, bytes))
        view?.setLayoutWidth(bytes)
    }

    private fun showStreamInputDialog(context: Context) {
        val dialog = Dialog(context, R.style.AlertDialogCustom)
        val binding = StreamInputBinding.inflate(dialog.layoutInflater)
        dialog.setContentView(binding.root)
        dialog.show()

        var preventAutoSwitching = false
        binding.hlsSwitch.setOnClickListener { preventAutoSwitching = true }

        binding.streamReferer.doOnTextChanged { text, _, _, _ ->
            if (!preventAutoSwitching) activateSwitchOnHls(text?.toString(), binding)
        }

        (activity?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.primaryClip?.getItemAt(0)?.text?.toString()?.let { copy ->
            val fixedText = copy.trim()
            binding.streamUrl.setText(fixedText)
            activateSwitchOnHls(fixedText, binding)
        }

        binding.applyBtt.setOnClickListener {
            val url = binding.streamUrl.text?.toString()
            if (url.isNullOrEmpty()) {
                showToast(R.string.error_invalid_url, Toast.LENGTH_SHORT)
            } else {
                val referer = binding.streamReferer.text?.toString()
                activity?.navigate(
                    R.id.global_to_navigation_player,
                    GeneratorPlayer.newInstance(
                        LinkGenerator(
                            listOf(BasicLink(url)),
                            extract = true,
                            referer = referer,
                            isM3u8 = binding.hlsSwitch.isChecked
                        )
                    )
                )
                dialog.dismissSafe(activity)
            }
        }

        binding.cancelBtt.setOnClickListener {
            dialog.dismissSafe(activity)
        }
    }

    private fun activateSwitchOnHls(text: String?, binding: StreamInputBinding) {
        binding.hlsSwitch.isChecked = normalSafeApiCall {
            URI(text).path?.substringAfterLast(".")?.contains("m3u")
        } == true
    }

    private fun handleScroll(dy: Int) {
        if (dy > 0) {
            binding?.downloadStreamButton?.shrink()
        } else if (dy < -5) {
            binding?.downloadStreamButton?.extend()
        }
    }
}