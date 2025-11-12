package com.lagradost.cloudstream3.ui.download

import android.app.Activity
import android.app.Dialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.format.Formatter.formatShortFileSize
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.activityViewModels
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentDownloadsBinding
import com.lagradost.cloudstream3.databinding.StreamInputBinding
import com.lagradost.cloudstream3.isEpisodeBased
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.ui.download.DownloadButtonSetup.handleDownloadClick
import com.lagradost.cloudstream3.ui.player.BasicLink
import com.lagradost.cloudstream3.ui.player.GeneratorPlayer
import com.lagradost.cloudstream3.ui.player.LinkGenerator
import com.lagradost.cloudstream3.ui.player.OfflinePlaybackHelper.playUri
import com.lagradost.cloudstream3.ui.result.FOCUS_SELF
import com.lagradost.cloudstream3.ui.result.setLinearListLayout
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLandscape
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.AppContextUtils.loadResult
import com.lagradost.cloudstream3.utils.BackPressedCallbackHelper.attachBackPressedCallback
import com.lagradost.cloudstream3.utils.BackPressedCallbackHelper.detachBackPressedCallback
import com.lagradost.cloudstream3.utils.DOWNLOAD_EPISODE_CACHE
import com.lagradost.cloudstream3.utils.DataStore.getFolderName
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.cloudstream3.utils.UIHelper.setAppBarNoScrollFlagsOnTV
import java.net.URI

const val DOWNLOAD_NAVIGATE_TO = "downloadpage"

class DownloadFragment : BaseFragment<FragmentDownloadsBinding>(
    BaseFragment.BindingCreator.Inflate(FragmentDownloadsBinding::inflate)
) {

    private val downloadViewModel: DownloadViewModel by activityViewModels()

    private fun View.setLayoutWidth(weight: Long) {
        val param = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.MATCH_PARENT,
            maxOf((weight / 1000000000f), 0.1f) // 100mb
        )
        this.layoutParams = param
    }

    override fun onDestroyView() {
        activity?.detachBackPressedCallback("Downloads")
        super.onDestroyView()
    }

    override fun fixLayout(view: View) {
        fixSystemBarsPadding(
            view,
            padBottom = isLandscape(),
            padLeft = isLayout(TV or EMULATOR)
        )
    }

    override fun onBindingCreated(binding: FragmentDownloadsBinding) {
        hideKeyboard()
        binding.downloadAppbar.setAppBarNoScrollFlagsOnTV()
        binding.downloadDeleteAppbar.setAppBarNoScrollFlagsOnTV()

        /**
         * We never want to retain multi-delete state
         * when navigating to downloads. Setting this state
         * immediately can sometimes result in the observer
         * not being notified in time to update the UI.
         *
         * By posting to the main looper, we ensure that this
         * operation is executed after the view has been fully created
         * and all initializations are completed, allowing the
         * observer to properly receive and handle the state change.
         */
        Handler(Looper.getMainLooper()).post {
            downloadViewModel.setIsMultiDeleteState(false)
        }

        /**
         * We have to make sure selected items are
         * cleared here as well so we don't run in an
         * inconsistent state where selected items do
         * not match the multi delete state we are in.
         */
        downloadViewModel.clearSelectedItems()

        observe(downloadViewModel.headerCards) {
            (binding.downloadList.adapter as? DownloadAdapter)?.submitList(it)
            binding.downloadLoading.isVisible = false
            binding.textNoDownloads.isVisible = it.isEmpty()
        }
        observe(downloadViewModel.availableBytes) {
            updateStorageInfo(
                binding.root.context,
                it,
                R.string.free_storage,
                binding.downloadFreeTxt,
                binding.downloadFree
            )
        }
        observe(downloadViewModel.usedBytes) {
            updateStorageInfo(
                binding.root.context,
                it,
                R.string.used_storage,
                binding.downloadUsedTxt,
                binding.downloadUsed
            )

            val hasBytes = it > 0
            if (hasBytes) {
                binding.downloadLoadingBytes.stopShimmer()
            } else binding.downloadLoadingBytes.startShimmer()

            binding.downloadBytesBar.isVisible = hasBytes
            binding.downloadLoadingBytes.isGone = hasBytes
        }
        observe(downloadViewModel.downloadBytes) {
            updateStorageInfo(
                binding.root.context,
                it,
                R.string.app_storage,
                binding.downloadAppTxt,
                binding.downloadApp
            )
        }
        observe(downloadViewModel.selectedBytes) {
            updateDeleteButton(downloadViewModel.selectedItemIds.value?.count() ?: 0, it)
        }
        observe(downloadViewModel.isMultiDeleteState) { isMultiDeleteState ->
            val adapter = binding.downloadList.adapter as? DownloadAdapter
            adapter?.setIsMultiDeleteState(isMultiDeleteState)
            binding.downloadDeleteAppbar.isVisible = isMultiDeleteState
            if (!isMultiDeleteState) {
                activity?.detachBackPressedCallback("Downloads")
                downloadViewModel.clearSelectedItems()
                // Prevent race condition and make sure
                // we don't display it early
                if (downloadViewModel.usedBytes.value?.let { it > 0 } == true) {
                    binding.downloadAppbar.isVisible = true
                }
            }
        }
        observe(downloadViewModel.selectedItemIds) {
            handleSelectedChange(it)
            updateDeleteButton(it.count(), downloadViewModel.selectedBytes.value ?: 0L)

            binding.btnDelete.isVisible = it.isNotEmpty()
            binding.selectItemsText.isVisible = it.isEmpty()

            val allSelected = downloadViewModel.isAllSelected()
            if (allSelected) {
                binding.btnToggleAll.setText(R.string.deselect_all)
            } else binding.btnToggleAll.setText(R.string.select_all)
        }

        val adapter = DownloadAdapter(
            { click -> handleItemClick(click) },
            { click ->
                if (click.action == DOWNLOAD_ACTION_DELETE_FILE) {
                    context?.let { ctx ->
                        downloadViewModel.handleSingleDelete(ctx, click.data.id)
                    }
                } else handleDownloadClick(click)
            },
            { itemId, isChecked ->
                if (isChecked) {
                    downloadViewModel.addSelected(itemId)
                } else downloadViewModel.removeSelected(itemId)
            }
        )

        binding.downloadList.apply {
            setHasFixedSize(true)
            setItemViewCacheSize(20)
            this.adapter = adapter
            setLinearListLayout(
                isHorizontal = false,
                nextRight = FOCUS_SELF,
                nextDown = FOCUS_SELF,
            )
        }

        binding.apply {
            openLocalVideoButton.apply {
                isGone = isLayout(TV)
                setOnClickListener { openLocalVideo() }
            }
            downloadStreamButton.apply {
                isGone = isLayout(TV)
                setOnClickListener { showStreamInputDialog(it.context) }
            }

            downloadStreamButtonTv.isFocusableInTouchMode = isLayout(TV)
            downloadAppbar.isFocusableInTouchMode = isLayout(TV)

            downloadStreamButtonTv.setOnClickListener { showStreamInputDialog(it.context) }
            steamImageviewHolder.isVisible = isLayout(TV)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            binding.downloadList.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                handleScroll(scrollY - oldScrollY)
            }
        }

        context?.let { downloadViewModel.updateHeaderList(it) }
    }

    private fun handleItemClick(click: DownloadHeaderClickEvent) {
        when (click.action) {
            DOWNLOAD_ACTION_GO_TO_CHILD -> {
                if (click.data.type.isEpisodeBased()) {
                    val folder =
                        getFolderName(DOWNLOAD_EPISODE_CACHE, click.data.id.toString())
                    activity?.navigate(
                        R.id.action_navigation_downloads_to_navigation_download_child,
                        DownloadChildFragment.newInstance(click.data.name, folder)
                    )
                }
            }

            DOWNLOAD_ACTION_LOAD_RESULT -> {
                activity?.loadResult(click.data.url, click.data.apiName, click.data.name)
            }
        }
    }

    private fun handleSelectedChange(selected: MutableSet<Int>) {
        if (selected.isNotEmpty()) {
            binding?.downloadDeleteAppbar?.isVisible = true
            binding?.downloadAppbar?.isVisible = false
            activity?.attachBackPressedCallback("Downloads") {
                downloadViewModel.setIsMultiDeleteState(false)
            }

            binding?.btnDelete?.setOnClickListener {
                context?.let { ctx ->
                    downloadViewModel.handleMultiDelete(ctx)
                }
            }

            binding?.btnCancel?.setOnClickListener {
                downloadViewModel.setIsMultiDeleteState(false)
            }

            binding?.btnToggleAll?.setOnClickListener {
                val allSelected = downloadViewModel.isAllSelected()
                val adapter = binding?.downloadList?.adapter as? DownloadAdapter
                if (allSelected) {
                    adapter?.notifySelectionStates()
                    downloadViewModel.clearSelectedItems()
                } else {
                    adapter?.notifyAllSelected()
                    downloadViewModel.selectAllItems()
                }
            }

            downloadViewModel.setIsMultiDeleteState(true)
        }
    }

    private fun updateDeleteButton(count: Int, selectedBytes: Long) {
        val formattedSize = formatShortFileSize(context, selectedBytes)
        binding?.btnDelete?.text =
            getString(R.string.delete_format).format(count, formattedSize)
    }

    private fun updateStorageInfo(
        context: Context,
        bytes: Long,
        @StringRes stringRes: Int,
        textView: TextView?,
        view: View?
    ) {
        textView?.text = getString(R.string.storage_size_format).format(
            getString(stringRes),
            formatShortFileSize(context, bytes)
        )
        view?.setLayoutWidth(bytes)
    }

    private fun openLocalVideo() {
        val intent = Intent()
            .setAction(Intent.ACTION_GET_CONTENT)
            .setType("video/*")
            .addCategory(Intent.CATEGORY_OPENABLE)
            .addFlags(FLAG_GRANT_READ_URI_PERMISSION) // Request temporary access
        safe {
            videoResultLauncher.launch(
                Intent.createChooser(
                    intent,
                    getString(R.string.open_local_video)
                )
            )
        }
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

        (activity?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.primaryClip?.getItemAt(
            0
        )?.text?.toString()?.let { copy ->
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
                            refererUrl = referer,
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
        binding.hlsSwitch.isChecked = safe {
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

    // Open local video from files using content provider x safeFile
    private val videoResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val selectedVideoUri = result.data?.data ?: return@registerForActivityResult
        playUri(activity ?: return@registerForActivityResult, selectedVideoUri)
    }
}