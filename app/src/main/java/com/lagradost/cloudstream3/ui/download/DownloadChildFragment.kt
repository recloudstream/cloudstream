package com.lagradost.cloudstream3.ui.download

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.Formatter.formatShortFileSize
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentChildDownloadsBinding
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.download.DownloadButtonSetup.handleDownloadClick
import com.lagradost.cloudstream3.ui.result.FOCUS_SELF
import com.lagradost.cloudstream3.ui.result.setLinearListLayout
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.PHONE
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.BackPressedCallbackHelper.attachBackPressedCallback
import com.lagradost.cloudstream3.utils.BackPressedCallbackHelper.detachBackPressedCallback
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbar
import com.lagradost.cloudstream3.utils.UIHelper.setAppBarNoScrollFlagsOnTV

class DownloadChildFragment : Fragment() {
    private lateinit var downloadsViewModel: DownloadViewModel
    private var binding: FragmentChildDownloadsBinding? = null

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
        binding = null
        super.onDestroyView()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        downloadsViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        val localBinding = FragmentChildDownloadsBinding.inflate(inflater, container, false)
        binding = localBinding
        return localBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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
            downloadsViewModel.setIsMultiDeleteState(false)
        }

        /**
         * We have to make sure selected items are
         * cleared here as well so we don't run in an
         * inconsistent state where selected items do
         * not match the multi delete state we are in.
         */
        downloadsViewModel.clearSelectedItems()

        val folder = arguments?.getString("folder")
        val name = arguments?.getString("name")
        if (folder == null) {
            activity?.onBackPressedDispatcher?.onBackPressed()
            return
        }

        binding?.downloadChildToolbar?.apply {
            title = name
            if (isLayout(PHONE or EMULATOR)) {
                setNavigationIcon(R.drawable.ic_baseline_arrow_back_24)
                setNavigationOnClickListener {
                    activity?.onBackPressedDispatcher?.onBackPressed()
                }
            }
            setAppBarNoScrollFlagsOnTV()
        }

        binding?.downloadDeleteAppbar?.setAppBarNoScrollFlagsOnTV()

        observe(downloadsViewModel.childCards) {
            if (it.isEmpty()) {
                activity?.onBackPressedDispatcher?.onBackPressed()
                return@observe
            }

            (binding?.downloadChildList?.adapter as? DownloadAdapter)?.submitList(it)
        }
        observe(downloadsViewModel.isMultiDeleteState) { isMultiDeleteState ->
            val adapter = binding?.downloadChildList?.adapter as? DownloadAdapter
            adapter?.setIsMultiDeleteState(isMultiDeleteState)
            binding?.downloadDeleteAppbar?.isVisible = isMultiDeleteState
            if (!isMultiDeleteState) {
                activity?.detachBackPressedCallback("Downloads")
                downloadsViewModel.clearSelectedItems()
                binding?.downloadChildToolbar?.isVisible = true
            }
        }
        observe(downloadsViewModel.selectedBytes) {
            updateDeleteButton(downloadsViewModel.selectedItemIds.value?.count() ?: 0, it)
        }
        observe(downloadsViewModel.selectedItemIds) {
            handleSelectedChange(it)
            updateDeleteButton(it.count(), downloadsViewModel.selectedBytes.value ?: 0L)

            binding?.btnDelete?.isVisible = it.isNotEmpty()
            binding?.selectItemsText?.isVisible = it.isEmpty()

            val allSelected = downloadsViewModel.isAllSelected()
            if (allSelected) {
                binding?.btnToggleAll?.setText(R.string.deselect_all)
            } else binding?.btnToggleAll?.setText(R.string.select_all)
        }

        val adapter = DownloadAdapter(
            {},
            { click ->
                if (click.action == DOWNLOAD_ACTION_DELETE_FILE) {
                    context?.let { ctx ->
                        downloadsViewModel.handleSingleDelete(ctx, click.data.id)
                    }
                } else handleDownloadClick(click)
            },
            { itemId, isChecked ->
                if (isChecked) {
                    downloadsViewModel.addSelected(itemId)
                } else downloadsViewModel.removeSelected(itemId)
            }
        )

        binding?.downloadChildList?.apply {
            setHasFixedSize(true)
            setItemViewCacheSize(20)
            this.adapter = adapter
            setLinearListLayout(
                isHorizontal = false,
                nextRight = FOCUS_SELF,
                nextDown = FOCUS_SELF,
            )
        }

        context?.let { downloadsViewModel.updateChildList(it, folder) }
        fixPaddingStatusbar(binding?.downloadChildRoot)
    }

    private fun handleSelectedChange(selected: MutableSet<Int>) {
        if (selected.isNotEmpty()) {
            binding?.downloadDeleteAppbar?.isVisible = true
            binding?.downloadChildToolbar?.isVisible = false
            activity?.attachBackPressedCallback("Downloads") {
                downloadsViewModel.setIsMultiDeleteState(false)
            }

            binding?.btnDelete?.setOnClickListener {
                context?.let { ctx ->
                    downloadsViewModel.handleMultiDelete(ctx)
                }
            }

            binding?.btnCancel?.setOnClickListener {
                downloadsViewModel.setIsMultiDeleteState(false)
            }

            binding?.btnToggleAll?.setOnClickListener {
                val allSelected = downloadsViewModel.isAllSelected()
                val adapter = binding?.downloadChildList?.adapter as? DownloadAdapter
                if (allSelected) {
                    adapter?.notifySelectionStates()
                    downloadsViewModel.clearSelectedItems()
                } else {
                    adapter?.notifyAllSelected()
                    downloadsViewModel.selectAllItems()
                }
            }

            downloadsViewModel.setIsMultiDeleteState(true)
        }
    }

    private fun updateDeleteButton(count: Int, selectedBytes: Long) {
        val formattedSize = formatShortFileSize(context, selectedBytes)
        binding?.btnDelete?.text =
            getString(R.string.delete_format).format(count, formattedSize)
    }
}