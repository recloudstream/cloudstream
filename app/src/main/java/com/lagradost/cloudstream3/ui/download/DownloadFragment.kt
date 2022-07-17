package com.lagradost.cloudstream3.ui.download

import android.app.Dialog
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.isMovieType
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.download.DownloadButtonSetup.handleDownloadClick
import com.lagradost.cloudstream3.ui.player.GeneratorPlayer
import com.lagradost.cloudstream3.ui.player.LinkGenerator
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTvSettings
import com.lagradost.cloudstream3.utils.AppUtils.loadResult
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.DOWNLOAD_EPISODE_CACHE
import com.lagradost.cloudstream3.utils.DataStore
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbar
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.cloudstream3.utils.VideoDownloadHelper
import com.lagradost.cloudstream3.utils.VideoDownloadManager
import kotlinx.android.synthetic.main.fragment_downloads.*
import kotlinx.android.synthetic.main.stream_input.*


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
        main {
            (download_list?.adapter as DownloadHeaderAdapter?)?.cardList = list
            download_list?.adapter?.notifyDataSetChanged()
        }
    }

    override fun onDestroyView() {
        if (downloadDeleteEventListener != null) {
            VideoDownloadManager.downloadDeleteEvent -= downloadDeleteEventListener!!
            downloadDeleteEventListener = null
        }
        (download_list?.adapter as DownloadHeaderAdapter?)?.killAdapter()
        super.onDestroyView()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        downloadsViewModel =
            ViewModelProvider(this)[DownloadViewModel::class.java]

        return inflater.inflate(R.layout.fragment_downloads, container, false)
    }

    private var downloadDeleteEventListener: ((Int) -> Unit)? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        hideKeyboard()

        observe(downloadsViewModel.noDownloadsText) {
            text_no_downloads.text = it
        }
        observe(downloadsViewModel.headerCards) {
            setList(it)
            download_loading.isVisible = false
        }
        observe(downloadsViewModel.availableBytes) {
            download_free_txt?.text =
                getString(R.string.storage_size_format).format(
                    getString(R.string.free_storage),
                    getBytesAsText(it)
                )
            download_free?.setLayoutWidth(it)
        }
        observe(downloadsViewModel.usedBytes) {
            download_used_txt?.text =
                getString(R.string.storage_size_format).format(
                    getString(R.string.used_storage),
                    getBytesAsText(it)
                )
            download_used?.setLayoutWidth(it)
        }
        observe(downloadsViewModel.downloadBytes) {
            download_app_txt?.text =
                getString(R.string.storage_size_format).format(
                    getString(R.string.app_storage),
                    getBytesAsText(it)
                )
            download_app?.setLayoutWidth(it)
            download_storage_appbar?.isVisible = it > 0
        }

        val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder> =
            DownloadHeaderAdapter(
                ArrayList(),
                { click ->
                    when (click.action) {
                        0 -> {
                            if (click.data.type.isMovieType()) {
                                //wont be called
                            } else {
                                val folder = DataStore.getFolderName(
                                    DOWNLOAD_EPISODE_CACHE,
                                    click.data.id.toString()
                                )
                                activity?.navigate(
                                    R.id.action_navigation_downloads_to_navigation_download_child,
                                    DownloadChildFragment.newInstance(click.data.name, folder)
                                )
                            }
                        }
                        1 -> {
                            (activity as AppCompatActivity?)?.loadResult(
                                click.data.url,
                                click.data.apiName
                            )
                        }
                    }

                },
                { downloadClickEvent ->
                    if (downloadClickEvent.data !is VideoDownloadHelper.DownloadEpisodeCached) return@DownloadHeaderAdapter
                    handleDownloadClick(activity, downloadClickEvent.data.name, downloadClickEvent)
                    if (downloadClickEvent.action == DOWNLOAD_ACTION_DELETE_FILE) {
                        context?.let { ctx ->
                            downloadsViewModel.updateList(ctx)
                        }
                    }
                }
            )

        downloadDeleteEventListener = { id ->
            val list = (download_list?.adapter as DownloadHeaderAdapter?)?.cardList
            if (list != null) {
                if (list.any { it.data.id == id }) {
                    context?.let { ctx ->
                        setList(ArrayList())
                        downloadsViewModel.updateList(ctx)
                    }
                }
            }
        }

        downloadDeleteEventListener?.let { VideoDownloadManager.downloadDeleteEvent += it }

        download_list?.adapter = adapter
        download_list?.layoutManager = GridLayoutManager(context, 1)
        download_stream_button?.isGone = context?.isTvSettings() == true
        download_stream_button?.setOnClickListener {
            val dialog =
                Dialog(it.context ?: return@setOnClickListener, R.style.AlertDialogCustom)
            dialog.setContentView(R.layout.stream_input)

            dialog.show()

            (activity?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager?)?.primaryClip?.getItemAt(
                0
            )?.text?.toString()?.let { copy ->
                dialog.stream_url?.setText(copy)
            }

            dialog.apply_btt?.setOnClickListener {
                val url = dialog.stream_url.text?.toString()
                if (url.isNullOrEmpty()) {
                    showToast(activity, R.string.error_invalid_url, Toast.LENGTH_SHORT)
                } else {
                    val referer = dialog.stream_referer.text?.toString()

                    activity?.navigate(
                        R.id.global_to_navigation_player,
                        GeneratorPlayer.newInstance(
                            LinkGenerator(
                                listOf(url),
                                extract = true,
                                referer = referer
                            )
                        )
                    )

                    dialog.dismissSafe(activity)
                }
            }

            dialog.cancel_btt?.setOnClickListener {
                dialog.dismissSafe(activity)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            download_list?.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                val dy = scrollY - oldScrollY
                if (dy > 0) { //check for scroll down
                    download_stream_button?.shrink() // hide
                } else if (dy < -5) {
                    download_stream_button?.extend() // show
                }
            }
        }
        downloadsViewModel.updateList(requireContext())

        context?.fixPaddingStatusbar(download_root)
    }
}