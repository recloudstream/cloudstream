package com.lagradost.cloudstream3.ui.download

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.Formatter.formatShortFileSize
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentChildDownloadsBinding
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.ui.download.DownloadButtonSetup.handleDownloadClick
import com.lagradost.cloudstream3.ui.result.FOCUS_SELF
import com.lagradost.cloudstream3.ui.result.setLinearListLayout
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.PHONE
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLandscape
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.BackPressedCallbackHelper.attachBackPressedCallback
import com.lagradost.cloudstream3.utils.BackPressedCallbackHelper.detachBackPressedCallback
import com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding
import com.lagradost.cloudstream3.utils.UIHelper.setAppBarNoScrollFlagsOnTV

class DownloadChildFragment : BaseFragment<FragmentChildDownloadsBinding>(
    BaseFragment.BindingCreator.Inflate(FragmentChildDownloadsBinding::inflate)
) {

    private val downloadViewModel: DownloadViewModel by activityViewModels()

    companion object {
        fun newInstance(headerName: String, folder: String): Bundle {
            return Bundle().apply {
                putString("folder", folder)
                putString("name", headerName)
            }
        }
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

    override fun onBindingCreated(binding: FragmentChildDownloadsBinding) {
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

        val folder = arguments?.getString("folder")
        val name = arguments?.getString("name")
        if (folder == null) {
            activity?.onBackPressedDispatcher?.onBackPressed()
            return
        }

        binding.downloadChildToolbar.apply {
            title = name
            if (isLayout(PHONE or EMULATOR)) {
                setNavigationIcon(R.drawable.ic_baseline_arrow_back_24)
                setNavigationOnClickListener {
                    activity?.onBackPressedDispatcher?.onBackPressed()
                }
            }
            setAppBarNoScrollFlagsOnTV()
        }

        binding.downloadDeleteAppbar.setAppBarNoScrollFlagsOnTV()

        observe(downloadViewModel.childCards) {
            if (it.isEmpty()) {
                activity?.onBackPressedDispatcher?.onBackPressed()
                return@observe
            }

            (binding.downloadChildList.adapter as? DownloadAdapter)?.submitList(it)
        }
        observe(downloadViewModel.isMultiDeleteState) { isMultiDeleteState ->
            val adapter = binding.downloadChildList.adapter as? DownloadAdapter
            adapter?.setIsMultiDeleteState(isMultiDeleteState)
            binding.downloadDeleteAppbar.isVisible = isMultiDeleteState
            if (!isMultiDeleteState) {
                activity?.detachBackPressedCallback("Downloads")
                downloadViewModel.clearSelectedItems()
                binding.downloadChildToolbar.isVisible = true
            }
        }
        observe(downloadViewModel.selectedBytes) {
            updateDeleteButton(downloadViewModel.selectedItemIds.value?.count() ?: 0, it)
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
            {},
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

        binding.downloadChildList.apply {
            setHasFixedSize(true)
            setItemViewCacheSize(20)
            this.adapter = adapter
            setLinearListLayout(
                isHorizontal = false,
                nextRight = FOCUS_SELF,
                nextDown = FOCUS_SELF,
            )
        }

        context?.let { downloadViewModel.updateChildList(it, folder) }
    }

    private fun handleSelectedChange(selected: MutableSet<Int>) {
        if (selected.isNotEmpty()) {
            binding?.downloadDeleteAppbar?.isVisible = true
            binding?.downloadChildToolbar?.isVisible = false
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
                val adapter = binding?.downloadChildList?.adapter as? DownloadAdapter
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
}