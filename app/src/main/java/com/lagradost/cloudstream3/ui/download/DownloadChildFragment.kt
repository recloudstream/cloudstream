package com.lagradost.cloudstream3.ui.download

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
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
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.getKeys
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbar
import com.lagradost.cloudstream3.utils.UIHelper.setAppBarNoScrollFlagsOnTV
import com.lagradost.cloudstream3.utils.VideoDownloadHelper
import com.lagradost.cloudstream3.utils.VideoDownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DownloadChildFragment : Fragment() {
    private lateinit var downloadsViewModel: DownloadViewModel

    companion object {
        fun newInstance(headerName: String, folder: String): Bundle {
            return Bundle().apply {
                putString("folder", folder)
                putString("name", headerName)
            }
        }
    }

    override fun onDestroyView() {
        downloadDeleteEventListener?.let { VideoDownloadManager.downloadDeleteEvent -= it }
        downloadDeleteEventListener = null
        binding = null
        super.onDestroyView()
    }

    private var binding: FragmentChildDownloadsBinding? = null

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

    private fun updateList(folder: String) = main {
        context?.let { ctx ->
            val data = withContext(Dispatchers.IO) { ctx.getKeys(folder) }
            val eps = withContext(Dispatchers.IO) {
                data.mapNotNull { key ->
                    context?.getKey<VideoDownloadHelper.DownloadEpisodeCached>(key)
                }.mapNotNull {
                    val info = VideoDownloadManager.getDownloadFileInfoAndUpdateSettings(ctx, it.id)
                        ?: return@mapNotNull null
                    VisualDownloadCached.Child(
                        currentBytes = info.fileLength,
                        totalBytes = info.totalBytes,
                        data = it,
                    )
                }
            }.sortedBy { it.data.episode + (it.data.season ?: 0) * 100000 }
            if (eps.isEmpty()) {
                activity?.onBackPressedDispatcher?.onBackPressed()
                return@main
            }

            (binding?.downloadChildList?.adapter as? DownloadAdapter)?.submitList(eps)
        }
    }

    private var downloadDeleteEventListener: ((Int) -> Unit)? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // We always want fresh selections
        // when navigating to downloads
        downloadsViewModel.clearSelectedItems()

        val folder = arguments?.getString("folder")
        val name = arguments?.getString("name")
        if (folder == null) {
            activity?.onBackPressedDispatcher?.onBackPressed() // TODO FIX
            return
        }
        fixPaddingStatusbar(binding?.downloadChildRoot)

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

        observe(downloadsViewModel.selectedItems) {
            handleSelectedChange(it)
            binding?.btnDelete?.text =
                getString(R.string.delete_count).format(it.count())
        }

        val adapter = DownloadAdapter(
            {},
            { downloadClickEvent ->
                handleDownloadClick(downloadClickEvent)
                if (downloadClickEvent.action == DOWNLOAD_ACTION_DELETE_FILE) {
                    setUpDownloadDeleteListener(folder)
                }
            },
            { card, isChecked ->
                if (isChecked) {
                    downloadsViewModel.addSelected(card)
                } else downloadsViewModel.removeSelected(card)
            },
            { card ->
                downloadsViewModel.addSelected(card)
                (binding?.downloadChildList?.adapter as? DownloadAdapter)?.updateSelectedItem(
                    card.data.id,
                    true
                )
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

        updateList(folder)
    }

    private fun handleSelectedChange(selected: MutableList<VisualDownloadCached>) {
        val adapter = binding?.downloadChildList?.adapter as? DownloadAdapter
        if (selected.isNotEmpty()) {
            binding?.downloadDeleteAppbar?.isVisible = true
            attachBackPressedCallback()

            binding?.btnDelete?.setOnClickListener {
                context?.let { ctx -> downloadsViewModel.handleMultiDelete(ctx) }
            }

            binding?.btnCancel?.setOnClickListener {
                adapter?.setIsMultiDeleteState(false)
                downloadsViewModel.clearSelectedItems()
            }

            binding?.btnSelectAll?.setOnClickListener {
                adapter?.selectAllItems()
                downloadsViewModel.selectAllItems()
            }

            adapter?.setIsMultiDeleteState(true)
        } else {
            binding?.downloadDeleteAppbar?.isVisible = false

            detachBackPressedCallback()
            adapter?.setIsMultiDeleteState(false)
            downloadsViewModel.clearSelectedItems()
        }
    }

    private fun setUpDownloadDeleteListener(folder: String) {
        downloadDeleteEventListener = { id: Int ->
            val list = (binding?.downloadChildList?.adapter as? DownloadAdapter)?.currentList
            if (list != null) {
                if (list.any { it.data.id == id }) {
                    updateList(folder)
                }
            }
        }
        downloadDeleteEventListener?.let { VideoDownloadManager.downloadDeleteEvent += it }
    }

    private var backPressedCallback: OnBackPressedCallback? = null

    private fun attachBackPressedCallback() {
        if (backPressedCallback == null) {
            backPressedCallback = object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    (binding?.downloadChildList?.adapter as? DownloadAdapter)?.setIsMultiDeleteState(false)
                    downloadsViewModel.clearSelectedItems()
                }
            }
        }

        backPressedCallback?.isEnabled = true

        activity?.onBackPressedDispatcher?.addCallback(
            activity ?: return,
            backPressedCallback ?: return
        )
    }

    private fun detachBackPressedCallback() {
        backPressedCallback?.isEnabled = false
    }
}