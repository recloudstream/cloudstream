package com.lagradost.cloudstream3.ui.download

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentChildDownloadsBinding
import com.lagradost.cloudstream3.ui.download.DownloadButtonSetup.handleDownloadClick
import com.lagradost.cloudstream3.ui.result.FOCUS_SELF
import com.lagradost.cloudstream3.ui.result.setLinearListLayout
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.getKeys
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbar
import com.lagradost.cloudstream3.utils.VideoDownloadHelper
import com.lagradost.cloudstream3.utils.VideoDownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DownloadChildFragment : Fragment() {
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
        binding = null
        super.onDestroyView()
    }

    var binding: FragmentChildDownloadsBinding? = null
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val localBinding = FragmentChildDownloadsBinding.inflate(inflater, container, false)
        binding = localBinding
        return localBinding.root//inflater.inflate(R.layout.fragment_child_downloads, container, false)
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
                    VisualDownloadChildCached(info.fileLength, info.totalBytes, it)
                }
            }.sortedBy { it.data.episode + (it.data.season ?: 0) * 100000 }
            if (eps.isEmpty()) {
                activity?.onBackPressed()
                return@main
            }

            (binding?.downloadChildList?.adapter as DownloadChildAdapter? ?: return@main).cardList =
                eps
            binding?.downloadChildList?.adapter?.notifyDataSetChanged()
        }
    }

    private var downloadDeleteEventListener: ((Int) -> Unit)? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val folder = arguments?.getString("folder")
        val name = arguments?.getString("name")
        if (folder == null) {
            activity?.onBackPressed() // TODO FIX
            return
        }
        fixPaddingStatusbar(binding?.downloadChildRoot)

        binding?.downloadChildToolbar?.apply {
            title = name
            setNavigationIcon(R.drawable.ic_baseline_arrow_back_24)
            setNavigationOnClickListener {
                activity?.onBackPressed()
            }
        }


        val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder> =
            DownloadChildAdapter(
                ArrayList(),
            ) { click ->
                handleDownloadClick(click)
            }

        downloadDeleteEventListener = { id: Int ->
            val list = (binding?.downloadChildList?.adapter as DownloadChildAdapter?)?.cardList
            if (list != null) {
                if (list.any { it.data.id == id }) {
                    updateList(folder)
                }
            }
        }

        downloadDeleteEventListener?.let { VideoDownloadManager.downloadDeleteEvent += it }

        binding?.downloadChildList?.adapter = adapter
        binding?.downloadChildList?.setLinearListLayout(
            isHorizontal = false,
            nextDown = FOCUS_SELF,
            nextRight = FOCUS_SELF
        )//layoutManager = GridLayoutManager(context, 1)

        updateList(folder)
    }
}