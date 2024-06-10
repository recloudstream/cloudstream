package com.lagradost.cloudstream3.ui.download

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.text.format.Formatter.formatShortFileSize
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentDownloadsBinding
import com.lagradost.cloudstream3.databinding.StreamInputBinding
import com.lagradost.cloudstream3.isEpisodeBased
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
import com.lagradost.cloudstream3.utils.Coroutines.runOnMainThread
import com.lagradost.cloudstream3.utils.DOWNLOAD_EPISODE_CACHE
import com.lagradost.cloudstream3.utils.DataStore
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbar
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.cloudstream3.utils.UIHelper.setAppBarNoScrollFlagsOnTV
import com.lagradost.cloudstream3.utils.VideoDownloadHelper
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

    @SuppressLint("NotifyDataSetChanged")
    private fun setList(list: List<VisualDownloadHeaderCached>) {
        main {
            (binding?.downloadList?.adapter as DownloadHeaderAdapter?)?.cardList = list
            binding?.downloadList?.adapter?.notifyDataSetChanged()
        }
    }

    override fun onDestroyView() {
        if (downloadDeleteEventListener != null) {
            VideoDownloadManager.downloadDeleteEvent -= downloadDeleteEventListener!!
            downloadDeleteEventListener = null
        }
        binding = null
        super.onDestroyView()
    }

    var binding: FragmentDownloadsBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        downloadsViewModel =
            ViewModelProvider(this)[DownloadViewModel::class.java]

        val localBinding = FragmentDownloadsBinding.inflate(inflater, container, false)
        binding = localBinding
        return localBinding.root//inflater.inflate(R.layout.fragment_downloads, container, false)
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
            binding?.downloadFreeTxt?.text =
                getString(R.string.storage_size_format).format(
                    getString(R.string.free_storage),
                    formatShortFileSize(view.context, it)
                )
            binding?.downloadFree?.setLayoutWidth(it)
        }
        observe(downloadsViewModel.usedBytes) {
            binding?.apply {
                downloadUsedTxt.text =
                    getString(R.string.storage_size_format).format(
                        getString(R.string.used_storage),
                        formatShortFileSize(view.context, it)
                    )
                downloadUsed.setLayoutWidth(it)
                downloadStorageAppbar.isVisible = it > 0
            }
        }
        observe(downloadsViewModel.downloadBytes) {
            binding?.apply {
                downloadAppTxt.text =
                    getString(R.string.storage_size_format).format(
                        getString(R.string.app_storage),
                        formatShortFileSize(view.context, it)
                    )
                downloadApp.setLayoutWidth(it)
            }
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
                    handleDownloadClick(downloadClickEvent)
                    if (downloadClickEvent.action == DOWNLOAD_ACTION_DELETE_FILE) {
                        context?.let { ctx ->
                            downloadsViewModel.updateList(ctx)
                        }
                    }
                }
            )

        downloadDeleteEventListener = { id ->
            val list = (binding?.downloadList?.adapter as DownloadHeaderAdapter?)?.cardList
            if (list != null) {
                if (list.any { it.data.id == id }) {
                    context?.let { ctx ->
                        downloadsViewModel.updateList(ctx)
                    }
                }
            }
        }

        downloadDeleteEventListener?.let { VideoDownloadManager.downloadDeleteEvent += it }

        binding?.downloadList?.apply {
            setHasFixedSize(true)
            setItemViewCacheSize(20)
            this.adapter = adapter
            setLinearListLayout(
                isHorizontal = false,
                nextRight = FOCUS_SELF,
                nextUp = FOCUS_SELF,
                nextDown = FOCUS_SELF
            )
            //layoutManager = GridLayoutManager(context, 1)

            val itemTouchHelper = ItemTouchHelper(SwipeToDeleteCallback(this.adapter as DownloadHeaderAdapter))
            itemTouchHelper.attachToRecyclerView(binding?.downloadList)
        }

        // Should be visible in emulator layout
        binding?.downloadStreamButton?.isGone = isLayout(TV)
        binding?.downloadStreamButton?.setOnClickListener {
            val dialog =
                Dialog(it.context ?: return@setOnClickListener, R.style.AlertDialogCustom)

            val binding = StreamInputBinding.inflate(dialog.layoutInflater)

            dialog.setContentView(binding.root)

            dialog.show()

            // If user has clicked the switch do not interfere
            var preventAutoSwitching = false
            binding.hlsSwitch.setOnClickListener {
                preventAutoSwitching = true
            }

            fun activateSwitchOnHls(text: String?) {
                binding.hlsSwitch.isChecked = normalSafeApiCall {
                    URI(text).path?.substringAfterLast(".")?.contains("m3u")
                } == true
            }

            binding.streamReferer.doOnTextChanged { text, _, _, _ ->
                if (!preventAutoSwitching)
                    activateSwitchOnHls(text?.toString())
            }

            (activity?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager?)?.primaryClip?.getItemAt(
                0
            )?.text?.toString()?.let { copy ->
                val fixedText = copy.trim()
                binding.streamUrl.setText(fixedText)
                activateSwitchOnHls(fixedText)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            binding?.downloadList?.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                val dy = scrollY - oldScrollY
                if (dy > 0) { //check for scroll down
                    binding?.downloadStreamButton?.shrink() // hide
                } else if (dy < -5) {
                    binding?.downloadStreamButton?.extend() // show
                }
            }
        }
        downloadsViewModel.updateList(requireContext())

        fixPaddingStatusbar(binding?.downloadRoot)
    }
}

class SwipeToDeleteCallback(private val adapter: DownloadHeaderAdapter) : ItemTouchHelper.Callback() {

    private var deleteInitiated = false

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        val position = viewHolder.bindingAdapterPosition
        val item = adapter.cardList[position]
        if (item.data.type.isEpisodeBased()) return 0
        return makeMovementFlags(ItemTouchHelper.LEFT, 0)
    }

    override fun isLongPressDragEnabled(): Boolean = true

    override fun isItemViewSwipeEnabled(): Boolean = false

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean = false

    override fun onSwiped(
        viewHolder: RecyclerView.ViewHolder,
        direction: Int
    ) {}

    private fun handleDelete(viewHolder: RecyclerView.ViewHolder) {
        if (deleteInitiated) return

        deleteInitiated = true
        val position = viewHolder.bindingAdapterPosition
        val item = adapter.cardList[position]

        runOnMainThread {
            item.child?.let { clickEvent ->
                handleDownloadClick(
                    DownloadClickEvent(
                        DOWNLOAD_ACTION_DELETE_FILE,
                        clickEvent
                    )
                ) { adapter.notifyItemRemoved(viewHolder.absoluteAdapterPosition) }
            }
        }
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        val deleteIcon: Drawable = ContextCompat.getDrawable(
            recyclerView.context,
            R.drawable.ic_baseline_delete_outline_24
        ) ?: return

        val background = ColorDrawable(Color.RED)

        val itemView = viewHolder.itemView

        val scaleFactor = 1.5f
        val iconWidth = (deleteIcon.intrinsicWidth * scaleFactor).toInt()
        val iconHeight = (deleteIcon.intrinsicHeight * scaleFactor).toInt()

        val iconMargin = (itemView.height - iconHeight) / 2
        val iconTop = itemView.top + (itemView.height - iconHeight) / 2
        val iconBottom = iconTop + iconHeight

        val swipeDistance = itemView.width / 4
        val limitedDX = if (dX < -swipeDistance) -swipeDistance.toFloat() else dX

        if (limitedDX < 0) { // Swiping to the left
            val iconLeft = itemView.right - iconMargin - iconWidth
            val iconRight = itemView.right - iconMargin
            deleteIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom)

            val path = Path()
            val rectF = RectF(
                itemView.right + limitedDX,
                itemView.top.toFloat(),
                itemView.right.toFloat(),
                itemView.bottom.toFloat()
            )

            val radii = floatArrayOf(0f, 0f, 20f, 20f, 20f, 20f, 0f, 0f)

            path.addRoundRect(rectF, radii, Path.Direction.CW)
            c.clipPath(path)

            background.setBounds(
                itemView.right + limitedDX.toInt(),
                itemView.top,
                itemView.right,
                itemView.bottom
            )
        } else background.setBounds(0, 0, 0, 0)

        background.draw(c)
        deleteIcon.draw(c)

        if (dX <= -swipeDistance && !isCurrentlyActive) {
            handleDelete(viewHolder)
        } else super.onChildDraw(c, recyclerView, viewHolder, limitedDX, dY, actionState, isCurrentlyActive)
    }

    override fun clearView(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ) {
        super.clearView(recyclerView, viewHolder)
        deleteInitiated = false
    }
}