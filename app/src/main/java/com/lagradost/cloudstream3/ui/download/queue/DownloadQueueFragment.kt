package com.lagradost.cloudstream3.ui.download.queue

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.Callback.makeMovementFlags
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentDownloadQueueBinding
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.services.DownloadQueueService
import com.lagradost.cloudstream3.ui.download.DownloadViewModel
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.PHONE
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setUpToolbar
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbar
import com.lagradost.cloudstream3.utils.UIHelper.setAppBarNoScrollFlagsOnTV
import com.lagradost.cloudstream3.utils.downloader.DownloadQueueManager
import com.lagradost.cloudstream3.utils.downloader.VideoDownloadManager
import com.lagradost.cloudstream3.utils.txt

class DownloadQueueFragment : Fragment() {
    private lateinit var queueViewModel: DownloadQueueViewModel
    private var binding: FragmentDownloadQueueBinding? = null

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        queueViewModel = ViewModelProvider(this)[DownloadQueueViewModel::class.java]
        val localBinding = FragmentDownloadQueueBinding.inflate(inflater, container, false)
        binding = localBinding
        return localBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding?.downloadQueueToolbar?.apply {
            title = txt(R.string.title_queue).asString(view.context)
            if (isLayout(PHONE or EMULATOR)) {
                setNavigationIcon(R.drawable.ic_baseline_arrow_back_24)
                setNavigationOnClickListener {
                    activity?.onBackPressedDispatcher?.onBackPressed()
                }
            }
            setAppBarNoScrollFlagsOnTV()
        }
        fixPaddingStatusbar(binding?.downloadQueueRoot)

        val adapter = DownloadQueueAdapter(this@DownloadQueueFragment)

        observe(queueViewModel.childCards) { cards ->
            adapter.submitList(cards)
        }

        binding?.apply {
            downloadChildList.adapter = adapter

            // Drag and drop
            val helper = DragAndDropTouchHelper(adapter)
            helper.attachToRecyclerView(downloadChildList)
        }
    }
}