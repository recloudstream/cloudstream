package com.lagradost.cloudstream3.ui.download

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbar
import com.lagradost.cloudstream3.isMovieType
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.download.DownloadButtonSetup.handleDownloadClick
import com.lagradost.cloudstream3.utils.DOWNLOAD_EPISODE_CACHE
import com.lagradost.cloudstream3.utils.DataStore.getFolderName
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import com.lagradost.cloudstream3.utils.VideoDownloadManager
import kotlinx.android.synthetic.main.fragment_downloads.*

const val DOWNLOAD_NAVIGATE_TO = "downloadpage"

class DownloadFragment : Fragment() {
    private lateinit var downloadsViewModel: DownloadViewModel

    private fun getBytesAsText(bytes: Long): String {
        return "%.1f".format(bytes / 1000000000f)
    }

    private fun View.setLayoutWidth(weight: Long) {
        val param = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.MATCH_PARENT,
            maxOf((weight / 1000000000f), 0.1f) // 100mb
        )
        this.layoutParams = param
    }

    private fun setList(list: List<VisualDownloadHeaderCached>) {
        (download_list?.adapter as DownloadHeaderAdapter?)?.cardList = list
        download_list?.adapter?.notifyDataSetChanged()
    }

    override fun onDestroyView() {
        (download_list?.adapter as DownloadHeaderAdapter?)?.killAdapter()
        super.onDestroyView()
    }

    override fun onDestroy() {
        downloadDeleteEventListener?.let { VideoDownloadManager.downloadDeleteEvent -= it }
        super.onDestroy()
    }

    @SuppressLint("SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        downloadsViewModel =
            ViewModelProvider(this).get(DownloadViewModel::class.java)
        observe(downloadsViewModel.noDownloadsText) {
            text_no_downloads.text = it
        }
        observe(downloadsViewModel.headerCards) {
            setList(it)
        }
        observe(downloadsViewModel.availableBytes) {
            download_free_txt?.text = "Free • ${getBytesAsText(it)}GB"
            download_free?.setLayoutWidth(it)
        }
        observe(downloadsViewModel.usedBytes) {
            download_used_txt?.text = "Used • ${getBytesAsText(it)}GB"
            download_used?.setLayoutWidth(it)
        }
        observe(downloadsViewModel.downloadBytes) {
            download_app_txt?.text = "App • ${getBytesAsText(it)}GB"
            download_app?.setLayoutWidth(it)
            download_storage_appbar?.visibility = View.VISIBLE
        }
        return inflater.inflate(R.layout.fragment_downloads, container, false)
    }

    var downloadDeleteEventListener: ((Int) -> Unit)? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        hideKeyboard()

        val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder> =
            DownloadHeaderAdapter(
                ArrayList(),
                { click ->
                    if (click.data.type.isMovieType()) {
                        //wont be called
                    } else {
                        val folder = getFolderName(DOWNLOAD_EPISODE_CACHE, click.data.id.toString())
                        val navController = activity?.findNavController(R.id.nav_host_fragment)
                        navController?.navigate(R.id.navigation_download_child, Bundle().apply {
                            putString("folder", folder)
                            putString("name", click.data.name)
                        })
                    }
                },
                { downloadClickEvent ->
                    handleDownloadClick(activity, downloadClickEvent.data.name, downloadClickEvent)
                    if (downloadClickEvent.action == DOWNLOAD_ACTION_DELETE_FILE) {
                        downloadsViewModel.updateList(requireContext())
                    }
                }
            )

        downloadDeleteEventListener = { id ->
            val list = (download_list?.adapter as DownloadHeaderAdapter?)?.cardList
            if (list != null) {
                if (list.any { it.data.id == id }) {
                    setList(ArrayList())
                    downloadsViewModel.updateList(requireContext())
                }
            }
        }

        downloadDeleteEventListener?.let { VideoDownloadManager.downloadDeleteEvent += it }

        download_list.adapter = adapter
        download_list.layoutManager = GridLayoutManager(context, 1)
        downloadsViewModel.updateList(requireContext())

        context?.fixPaddingStatusbar(download_root)
    }
}