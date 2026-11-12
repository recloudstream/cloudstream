package com.lagradost.cloudstream3.ui.player

import android.view.View
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.isVisible
import androidx.navigation.findNavController
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentMultiviewPlayerBinding
import com.lagradost.cloudstream3.databinding.MultiviewStreamItemBinding
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setSystemBarsPadding
import com.lagradost.cloudstream3.utils.AppContextUtils.setDefaultFocus
import com.lagradost.cloudstream3.utils.UIHelper.popCurrentPage

enum class MultiViewMode {
    SPLIT_1X2,
    FOCUS_1PLUS2,
    GRID_2X2,
    GRID_3X3
}

class MultiViewPlayerFragment : BaseFragment<FragmentMultiviewPlayerBinding>(
    BaseFragment.BindingCreator.Inflate(FragmentMultiviewPlayerBinding::inflate)
) {
    private var currentMode = MultiViewMode.GRID_2X2
    private var activeFocusSlotIndex = 0

    override fun fixLayout(view: View) {
        setSystemBarsPadding()
    }

    override fun onBindingCreated(binding: FragmentMultiviewPlayerBinding) {
        setupBottomBar(binding)
        updateAllSlots(binding)
        applyLayoutMode(binding)
    }

    private fun setupBottomBar(binding: FragmentMultiviewPlayerBinding) {
        binding.btnAddStream.setOnClickListener {
            openStreamPickerForEmptySlot()
        }

        binding.btnMode1x2.setOnClickListener {
            currentMode = MultiViewMode.SPLIT_1X2
            updateAllSlots(binding)
            applyLayoutMode(binding)
        }

        binding.btnMode1plus2.setOnClickListener {
            currentMode = MultiViewMode.FOCUS_1PLUS2
            updateAllSlots(binding)
            applyLayoutMode(binding)
        }

        binding.btnModeGrid.setOnClickListener {
            currentMode = MultiViewMode.GRID_2X2
            updateAllSlots(binding)
            applyLayoutMode(binding)
        }

        binding.btnModeGrid3x3.setOnClickListener {
            currentMode = MultiViewMode.GRID_3X3
            updateAllSlots(binding)
            applyLayoutMode(binding)
        }

        binding.btnModeQuality.setOnClickListener { view ->
            val popup = androidx.appcompat.widget.PopupMenu(view.context, view)
            popup.menu.add(0, Int.MAX_VALUE, 0, "Auto (Full Quality)")
            popup.menu.add(0, 720, 1, "720p HD")
            popup.menu.add(0, 480, 2, "480p SD")
            popup.menu.add(0, 360, 3, "360p Low")

            popup.setOnMenuItemClickListener { menuItem ->
                val height = menuItem.itemId
                MultiPlayerManager.setGlobalQualityCap(height)
                val label = when (height) {
                    720 -> "Quality: 720p"
                    480 -> "Quality: 480p"
                    360 -> "Quality: 360p"
                    else -> "Quality: Auto"
                }
                binding.btnModeQuality.text = label
                true
            }
            popup.show()
        }

        binding.btnExitMultiview.setOnClickListener {
            returnToSinglePlayer()
        }

        binding.btnCloseAllMultiview.setOnClickListener {
            android.util.Log.d("MultiViewPlayer", "Close All clicked - releasing all players and returning home")
            MultiPlayerManager.releaseAll()
            activity?.let { act ->
                act.supportFragmentManager.popBackStack("MultiViewPlayer", androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
                act.popCurrentPage("MultiViewPlayer")
            } ?: dispatchBackPressed()
        }
    }

    private fun getSlotIncludeView(slotIndex: Int, binding: FragmentMultiviewPlayerBinding): View {
        return when (slotIndex) {
            0 -> binding.multiviewSlot0.root
            1 -> binding.multiviewSlot1.root
            2 -> binding.multiviewSlot2.root
            3 -> binding.multiviewSlot3.root
            4 -> binding.multiviewSlot4.root
            5 -> binding.multiviewSlot5.root
            6 -> binding.multiviewSlot6.root
            7 -> binding.multiviewSlot7.root
            8 -> binding.multiviewSlot8.root
            else -> binding.multiviewSlot0.root
        }
    }

    private fun getSlotBinding(slotIndex: Int, binding: FragmentMultiviewPlayerBinding): MultiviewStreamItemBinding {
        return MultiviewStreamItemBinding.bind(getSlotIncludeView(slotIndex, binding))
    }

    private fun updateAllSlots(binding: FragmentMultiviewPlayerBinding) {
        val maxVisibleSlots = when (currentMode) {
            MultiViewMode.SPLIT_1X2 -> 2
            MultiViewMode.FOCUS_1PLUS2 -> 3
            MultiViewMode.GRID_2X2 -> 4
            MultiViewMode.GRID_3X3 -> 9
        }

        for (i in 0 until MultiPlayerManager.MAX_SLOTS) {
            val includeView = getSlotIncludeView(i, binding)
            if (i < maxVisibleSlots) {
                includeView.isVisible = true
                val slotBinding = getSlotBinding(i, binding)
                bindSlot(i, slotBinding, binding)
            } else {
                includeView.isVisible = false
            }
        }
    }

    private fun bindSlot(
        slotIndex: Int,
        slotBinding: MultiviewStreamItemBinding,
        fragmentBinding: FragmentMultiviewPlayerBinding
    ) {
        val slot = MultiPlayerManager.getSlot(slotIndex) ?: return
        if (slot.url != null) {
            bindActiveSlot(slotIndex, slot, slotBinding, fragmentBinding)
        } else {
            bindEmptySlot(slotBinding)
        }
    }

    private fun bindActiveSlot(
        slotIndex: Int,
        slot: MultiStreamSlot,
        slotBinding: MultiviewStreamItemBinding,
        fragmentBinding: FragmentMultiviewPlayerBinding
    ) {
        slotBinding.apply {
            if (slot.player == null && slot.initialLink != null && !slot.isOffline) {
                context?.let { ctx ->
                    MultiPlayerManager.loadStream(
                        ctx,
                        slotIndex,
                        slot.title ?: "Stream ${slotIndex + 1}",
                        slot.initialLink!!,
                        multiviewPlayerView
                    )
                }
            }

            multiviewPlayerView.isVisible = true
            multiviewHeaderOverlay.isVisible = true
            multiviewMuteBtn.isVisible = true
            multiviewEmptySlotContainer.isVisible = false

            multiviewStreamTitle.text = slot.title ?: "Stream ${slotIndex + 1}"

            multiviewOfflineCard.isVisible = slot.isOffline
            if (slot.isOffline) {
                multiviewOfflineText.text = "${slot.title ?: "Stream"} is offline"
            }

            setupVolumeControls(slotIndex, slotBinding)

            val onCloseClick = {
                val countBefore = MultiPlayerManager.slots.count { it.url != null && !it.isOffline }
                MultiPlayerManager.closeSlot(slotIndex)
                updateAllSlots(fragmentBinding)
                val countAfter = MultiPlayerManager.slots.count { it.url != null && !it.isOffline }
                if (countBefore > 1 && countAfter == 1) {
                    promptSingleStreamSwitch()
                }
            }
            multiviewCloseBtn.setOnClickListener { onCloseClick() }
            multiviewOfflineCloseBtn.setOnClickListener { onCloseClick() }

            multiviewItemRoot.setOnClickListener {
                activeFocusSlotIndex = slotIndex
                MultiPlayerManager.setFocusedSlot(slotIndex)
                updateAllSlots(fragmentBinding)
                if (currentMode == MultiViewMode.FOCUS_1PLUS2) {
                    applyLayoutMode(fragmentBinding)
                }
            }
        }
    }

    private fun setupVolumeControls(slotIndex: Int, slotBinding: MultiviewStreamItemBinding) {
        slotBinding.apply {
            val currentVol = MultiPlayerManager.getVolume(slotIndex)
            multiviewVolumeSlider.progress = (currentVol * 100).toInt()

            val muteIcon = when {
                currentVol <= 0f -> R.drawable.ic_baseline_volume_mute_24
                currentVol < 0.5f -> R.drawable.ic_baseline_volume_down_24
                else -> R.drawable.ic_baseline_volume_up_24
            }
            multiviewMuteBtn.setImageResource(muteIcon)

            multiviewVolumeContainer.setOnHoverListener { _, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_HOVER_ENTER, android.view.MotionEvent.ACTION_HOVER_MOVE -> {
                        multiviewVolumeSlider.isVisible = true
                    }
                    android.view.MotionEvent.ACTION_HOVER_EXIT -> {
                        if (!multiviewVolumeSlider.isPressed) {
                            multiviewVolumeSlider.isVisible = false
                        }
                    }
                }
                false
            }

            multiviewMuteBtn.setOnClickListener {
                multiviewVolumeSlider.isVisible = !multiviewVolumeSlider.isVisible
            }

            multiviewVolumeSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val vol = progress / 100f
                        MultiPlayerManager.setVolume(slotIndex, vol)
                        val icon = when {
                            vol <= 0f -> R.drawable.ic_baseline_volume_mute_24
                            vol < 0.5f -> R.drawable.ic_baseline_volume_down_24
                            else -> R.drawable.ic_baseline_volume_up_24
                        }
                        multiviewMuteBtn.setImageResource(icon)
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
    }

    private fun bindEmptySlot(slotBinding: MultiviewStreamItemBinding) {
        slotBinding.apply {
            multiviewPlayerView.isVisible = false
            multiviewHeaderOverlay.isVisible = false
            multiviewMuteBtn.isVisible = false
            multiviewOfflineCard.isVisible = false
            multiviewEmptySlotContainer.isVisible = true

            multiviewSlotAddBtn.setOnClickListener {
                openStreamPickerForSlot()
            }
        }
    }

    private fun returnToSinglePlayer() {
        val activeSlot = MultiPlayerManager.getSlot(MultiPlayerManager.activeFocusSlotIndex)
            ?.takeIf { it.url != null && !it.isOffline }
            ?: MultiPlayerManager.slots.firstOrNull { it.url != null && !it.isOffline }
        val activeLink = activeSlot?.initialLink
        MultiPlayerManager.releaseAll()

        activity?.let { act ->
            act.supportFragmentManager.popBackStack("MultiViewPlayer", androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
            if (activeLink != null) {
                val intentData = GeneratorPlayer.newInstance(
                    ExtractorLinkGenerator(listOf(activeLink), emptyList()), 0
                )
                try {
                    act.findNavController(R.id.nav_host_fragment)?.navigate(
                        R.id.global_to_navigation_player,
                        intentData
                    )
                } catch (_: Throwable) {
                    act.popCurrentPage("MultiViewPlayer")
                }
            } else {
                act.popCurrentPage("MultiViewPlayer")
            }
        } ?: dispatchBackPressed()
    }

    private fun promptSingleStreamSwitch() {
        android.util.Log.d("MultiViewPlayer", "Active streams dropped to 1. Prompting user.")
        context?.let { ctx ->
            AlertDialog.Builder(ctx, R.style.AlertDialogCustom)
                .setTitle("Switch to Single Stream?")
                .setMessage("Only 1 stream is active. Would you like to return to full-screen player mode?")
                .setPositiveButton(R.string.yes) { _, _ ->
                    android.util.Log.d("MultiViewPlayer", "User clicked YES - returning to single player")
                    returnToSinglePlayer()
                }
                .setNegativeButton(R.string.no) { dialog, _ ->
                    android.util.Log.d("MultiViewPlayer", "User clicked NO - staying in Multi-View")
                    dialog.dismiss()
                }
                .show()
                .setDefaultFocus()
        }
    }

    private fun openStreamPickerForEmptySlot() {
        openStreamPickerForSlot()
    }

    private fun openStreamPickerForSlot() {
        val maxSlots = when (currentMode) {
            MultiViewMode.SPLIT_1X2 -> 2
            MultiViewMode.FOCUS_1PLUS2 -> 3
            MultiViewMode.GRID_2X2 -> 4
            MultiViewMode.GRID_3X3 -> 9
        }
        val emptySlots = (0 until maxSlots).filter {
            MultiPlayerManager.getSlot(it)?.url == null
        }
        if (emptySlots.isEmpty()) return
        val dialog = MultiViewStreamPickerDialog(maxCapacity = emptySlots.size) { selectedStreams ->
            val binding = binding ?: return@MultiViewStreamPickerDialog
            context?.let { ctx ->
                val availableSlotIndices = (0 until maxSlots).filter {
                    MultiPlayerManager.getSlot(it)?.url == null
                }
                selectedStreams.zip(availableSlotIndices).forEach { (stream, emptySlotIndex) ->
                    val (link, title) = stream
                    val slotBinding = getSlotBinding(emptySlotIndex, binding)
                    MultiPlayerManager.loadStream(ctx, emptySlotIndex, title, link, slotBinding.multiviewPlayerView)
                }
                updateAllSlots(binding)
            }
        }
        activity?.let { dialog.show(it.supportFragmentManager, "MultiViewStreamPicker") }
    }

    private fun applyLayoutMode(binding: FragmentMultiviewPlayerBinding) {
        when (currentMode) {
            MultiViewMode.SPLIT_1X2 -> apply1x2SplitConstraints(binding)
            MultiViewMode.FOCUS_1PLUS2 -> apply1Plus2Constraints(binding, activeFocusSlotIndex)
            MultiViewMode.GRID_2X2 -> apply2x2GridConstraints(binding)
            MultiViewMode.GRID_3X3 -> apply3x3GridConstraints(binding)
        }
    }

    private fun apply1x2SplitConstraints(binding: FragmentMultiviewPlayerBinding) {
        val constraintSet = ConstraintSet()
        constraintSet.clone(binding.multiviewViewport)

        val id0 = R.id.multiview_slot_0
        val id1 = R.id.multiview_slot_1

        constraintSet.clear(id0)
        constraintSet.clear(id1)

        // Slot 0 (Left 50%)
        constraintSet.connect(id0, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        constraintSet.connect(id0, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        constraintSet.connect(id0, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        constraintSet.connect(id0, ConstraintSet.END, id1, ConstraintSet.START)

        // Slot 1 (Right 50%)
        constraintSet.connect(id1, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        constraintSet.connect(id1, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        constraintSet.connect(id1, ConstraintSet.START, id0, ConstraintSet.END)
        constraintSet.connect(id1, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)

        constraintSet.applyTo(binding.multiviewViewport)
    }

    private fun apply1Plus2Constraints(binding: FragmentMultiviewPlayerBinding, focusSlotIndex: Int) {
        val constraintSet = ConstraintSet()
        constraintSet.clone(binding.multiviewViewport)

        val slots = intArrayOf(
            R.id.multiview_slot_0,
            R.id.multiview_slot_1,
            R.id.multiview_slot_2
        )

        val focusId = slots[focusSlotIndex.coerceIn(0, 2)]
        val sideIds = slots.filterIndexed { index, _ -> index != focusSlotIndex.coerceIn(0, 2) }

        // Primary Focused Slot takes ~67% width on Left
        constraintSet.clear(focusId)
        constraintSet.connect(focusId, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        constraintSet.connect(focusId, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        constraintSet.connect(focusId, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        constraintSet.connect(focusId, ConstraintSet.END, sideIds[0], ConstraintSet.START)

        // Top Right Small Slot
        constraintSet.clear(sideIds[0])
        constraintSet.connect(sideIds[0], ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        constraintSet.connect(sideIds[0], ConstraintSet.BOTTOM, sideIds[1], ConstraintSet.TOP)
        constraintSet.connect(sideIds[0], ConstraintSet.START, focusId, ConstraintSet.END)
        constraintSet.connect(sideIds[0], ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)

        // Bottom Right Small Slot
        constraintSet.clear(sideIds[1])
        constraintSet.connect(sideIds[1], ConstraintSet.TOP, sideIds[0], ConstraintSet.BOTTOM)
        constraintSet.connect(sideIds[1], ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        constraintSet.connect(sideIds[1], ConstraintSet.START, focusId, ConstraintSet.END)
        constraintSet.connect(sideIds[1], ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)

        constraintSet.applyTo(binding.multiviewViewport)
    }

    private fun apply2x2GridConstraints(binding: FragmentMultiviewPlayerBinding) {
        val constraintSet = ConstraintSet()
        constraintSet.clone(binding.multiviewViewport)

        val slots = intArrayOf(
            R.id.multiview_slot_0,
            R.id.multiview_slot_1,
            R.id.multiview_slot_2,
            R.id.multiview_slot_3
        )

        // Slot 0 (Top Left)
        constraintSet.connect(slots[0], ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        constraintSet.connect(slots[0], ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        constraintSet.connect(slots[0], ConstraintSet.END, slots[1], ConstraintSet.START)
        constraintSet.connect(slots[0], ConstraintSet.BOTTOM, slots[2], ConstraintSet.TOP)

        // Slot 1 (Top Right)
        constraintSet.connect(slots[1], ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        constraintSet.connect(slots[1], ConstraintSet.START, slots[0], ConstraintSet.END)
        constraintSet.connect(slots[1], ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
        constraintSet.connect(slots[1], ConstraintSet.BOTTOM, slots[3], ConstraintSet.TOP)

        // Slot 2 (Bottom Left)
        constraintSet.connect(slots[2], ConstraintSet.TOP, slots[0], ConstraintSet.BOTTOM)
        constraintSet.connect(slots[2], ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        constraintSet.connect(slots[2], ConstraintSet.END, slots[3], ConstraintSet.START)
        constraintSet.connect(slots[2], ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)

        // Slot 3 (Bottom Right)
        constraintSet.connect(slots[3], ConstraintSet.TOP, slots[1], ConstraintSet.BOTTOM)
        constraintSet.connect(slots[3], ConstraintSet.START, slots[2], ConstraintSet.END)
        constraintSet.connect(slots[3], ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
        constraintSet.connect(slots[3], ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)

        constraintSet.applyTo(binding.multiviewViewport)
    }

    private fun apply3x3GridConstraints(binding: FragmentMultiviewPlayerBinding) {
        val constraintSet = ConstraintSet()
        constraintSet.clone(binding.multiviewViewport)

        val slots = intArrayOf(
            R.id.multiview_slot_0, R.id.multiview_slot_1, R.id.multiview_slot_2,
            R.id.multiview_slot_3, R.id.multiview_slot_4, R.id.multiview_slot_5,
            R.id.multiview_slot_6, R.id.multiview_slot_7, R.id.multiview_slot_8
        )

        for (r in 0..2) {
            for (c in 0..2) {
                val index = r * 3 + c
                val id = slots[index]
                constraintSet.clear(id)

                if (r == 0) {
                    constraintSet.connect(id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                } else {
                    constraintSet.connect(id, ConstraintSet.TOP, slots[(r - 1) * 3 + c], ConstraintSet.BOTTOM)
                }

                if (r == 2) {
                    constraintSet.connect(id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
                } else {
                    constraintSet.connect(id, ConstraintSet.BOTTOM, slots[(r + 1) * 3 + c], ConstraintSet.TOP)
                }

                if (c == 0) {
                    constraintSet.connect(id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                } else {
                    constraintSet.connect(id, ConstraintSet.START, slots[r * 3 + (c - 1)], ConstraintSet.END)
                }

                if (c == 2) {
                    constraintSet.connect(id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                } else {
                    constraintSet.connect(id, ConstraintSet.END, slots[r * 3 + (c + 1)], ConstraintSet.START)
                }
            }
        }

        constraintSet.applyTo(binding.multiviewViewport)
    }

    override fun onDestroyView() {
        MultiPlayerManager.releaseAll()
        super.onDestroyView()
    }
}
