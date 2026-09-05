package com.lagradost.cloudstream3.ui.result

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.ActorFilmographyBinding
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.BaseBottomSheetDialogFragment
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.ui.quicksearch.QuickSearchFragment
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_LOAD
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_PLAY_FILE
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_SHOW_METADATA
import com.lagradost.cloudstream3.ui.search.SearchAdapter
import com.lagradost.cloudstream3.ui.setRecycledViewPool
import com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding
import com.lagradost.cloudstream3.utils.UIHelper.getSpanCount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Arguments and view-scoped work allow dismissal and Activity recreation during a lookup. */
class ActorFilmography : BaseBottomSheetDialogFragment<ActorFilmographyBinding>(
    BaseFragment.BindingCreator.Inflate(ActorFilmographyBinding::inflate)
) {
    companion object {
        private const val TAG = "actor_filmography"
        private const val ACTOR_NAME = "actor_name"
        private const val ACTOR_IMAGE = "actor_image"

        fun show(context: Context, actor: Actor) {
            val manager = (context.getActivity() as? FragmentActivity)?.supportFragmentManager
                ?: return
            if (manager.isStateSaved || manager.findFragmentByTag(TAG) != null) return

            ActorFilmography().apply {
                arguments = Bundle().apply {
                    putString(ACTOR_NAME, actor.name)
                    putString(ACTOR_IMAGE, actor.image)
                }
            }.showNow(manager, TAG)
        }
    }

    private var loadJob: Job? = null
    private val repository = ActorFilmographyRepository()

    override fun onStart() {
        super.onStart()
        // Dialog views gain layout parameters only after being attached to their window.
        view?.let { fixLayout(it) }
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun fixLayout(view: View) {
        fixSystemBarsPadding(view)
        view.layoutParams?.let {
            it.height = (resources.displayMetrics.heightPixels * 0.85).toInt()
            view.layoutParams = it
        }
        binding?.filmographyResults?.spanCount = view.context.getSpanCount()
    }

    override fun onBindingCreated(binding: ActorFilmographyBinding) {
        binding.filmographyActor.text = arguments?.getString(ACTOR_NAME)
        binding.filmographyClose.setOnClickListener { dismiss() }
        binding.filmographyResults.apply {
            spanCount = context.getSpanCount()
            setRecycledViewPool(SearchAdapter.sharedPool)
            adapter = SearchAdapter(this) { callback ->
                when (callback.action) {
                    SEARCH_ACTION_LOAD,
                    SEARCH_ACTION_SHOW_METADATA,
                    SEARCH_ACTION_PLAY_FILE -> {
                        // Never send a TMDB URL to a streaming provider's load/play handlers.
                        QuickSearchFragment.pushSearch(activity, callback.card.name)
                        dismiss()
                    }
                }
            }
        }
        binding.filmographyRetry.setOnClickListener { loadFilmography() }
        loadFilmography()
    }

    private fun loadFilmography() {
        val binding = binding ?: return
        val actor = Actor(
            name = arguments?.getString(ACTOR_NAME).orEmpty(),
            image = arguments?.getString(ACTOR_IMAGE),
        )
        loadJob?.cancel()
        binding.filmographyLoading.isVisible = true
        binding.filmographyStatus.isVisible = false
        binding.filmographyRetry.isVisible = false
        binding.filmographyResults.isVisible = false

        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val credits = withContext(Dispatchers.IO) { repository.load(actor) }
                (binding.filmographyResults.adapter as? SearchAdapter)?.submitList(credits)
                binding.filmographyResults.isVisible = credits.isNotEmpty()
                binding.filmographyStatus.setText(R.string.actor_filmography_empty)
                binding.filmographyStatus.isVisible = credits.isEmpty()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                logError(error)
                binding.filmographyStatus.setText(R.string.actor_filmography_error)
                binding.filmographyStatus.isVisible = true
                binding.filmographyRetry.isVisible = true
            } finally {
                binding.filmographyLoading.isVisible = false
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        loadJob?.cancel()
        super.onDismiss(dialog)
    }

    override fun onDestroyView() {
        loadJob?.cancel()
        loadJob = null
        binding?.filmographyResults?.adapter = null
        super.onDestroyView()
    }
}
