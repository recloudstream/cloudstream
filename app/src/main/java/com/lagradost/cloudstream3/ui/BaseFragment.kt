package com.lagradost.cloudstream3.ui

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.txt
import com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding

/**
 * A base Fragment class that simplifies ViewBinding usage and handles view inflation safely.
 *
 * This class allows two modes of creating ViewBinding:
 * 1. Inflate: Using the standard `inflate()` method provided by generated ViewBinding classes.
 * 2. Bind: Using `bind()` on an existing root view.
 *
 * It also provides hooks for:
 * - Safe initialization of the binding (`onBindingCreated`)
 * - Automatic padding adjustment for system bars (`fixPadding`)
 * - Optional layout resource selection via `pickLayout()`
 *
 * @param T The type of ViewBinding for this Fragment.
 * @param bindingCreator The strategy used to create the binding instance.
 */
abstract class BaseFragment<T : ViewBinding>(
    private val bindingCreator: BindingCreator<T>
) : Fragment() {

    private var _binding: T? = null
    protected val binding: T? get() = _binding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val layoutId = pickLayout()
        val root: View? = layoutId?.let { inflater.inflate(it, container, false) }
        _binding = try {
            when (bindingCreator) {
                is BindingCreator.Inflate -> bindingCreator.fn(inflater, container, false)
                is BindingCreator.Bind -> {
                    if (root != null) bindingCreator.fn(root)
                    else throw IllegalStateException("Root view is null for bind()")
                }
            }
        } catch (t: Throwable) {
            showToast(
                txt(R.string.unable_to_inflate, t.message ?: ""),
                Toast.LENGTH_LONG
            )
            logError(t)
            null
        }

        return _binding?.root ?: root
    }

    /**
     * Called after the fragment's view has been created.
     *
     * This method is `final` to ensure that the binding is properly initialized and
     * system bar padding adjustments are applied before any subclass logic runs.
     * Subclasses should use [onBindingCreated] instead of overriding this method directly.
     */
    final override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fixPadding(view)
        binding?.let { onBindingCreated(it, savedInstanceState) }
    }

    /**
     * Called when the binding is safely created and view is ready.
     * Can be overridden to provide fragment-specific initialization.
     *
     * @param binding The safely created ViewBinding.
     * @param savedInstanceState Saved state bundle or null.
     */
    protected open fun onBindingCreated(binding: T, savedInstanceState: Bundle?) {
        onBindingCreated(binding)
    }

    /**
     * Called when the binding is safely created and view is ready.
     * Overload without savedInstanceState for convenience.
     *
     * @param binding The safely created ViewBinding.
     */
    protected open fun onBindingCreated(binding: T) {}

    /**
     * Called when the device configuration changes (e.g., orientation).
     * Re-applies system bar padding fixes to the root view to ensure it
     * readjusts for orientation changes.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        binding?.apply { fixPadding(root) }
        super.onConfigurationChanged(newConfig)
    }

    /** Cleans up the binding reference when the view is destroyed to avoid memory leaks. */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Pick a layout resource ID for the fragment.
     *
     * Return `null` by default. Override to provide a layout resource when using
     * `BindingCreator.Bind`. Not needed if using `BindingCreator.Inflate`.
     *
     * @return Layout resource ID or null.
     */
    @LayoutRes
    protected open fun pickLayout(): Int? = null

    /**
     * Apply padding adjustments for system bars to the root view.
     *
     * @param view The root view to adjust.
     */
    protected open fun fixPadding(view: View) {
        fixSystemBarsPadding(view)
    }

    /**
     * Sealed class representing the two strategies for creating a ViewBinding instance.
     */
    sealed class BindingCreator<T : ViewBinding> {

        /**
         * Use the standard inflate() method for creating the binding.
         *
         * @param fn Lambda that inflates the binding.
         */
        class Inflate<T : ViewBinding>(
            val fn: (LayoutInflater, ViewGroup?, Boolean) -> T
        ) : BindingCreator<T>()

        /**
         * Use bind() on an existing root view to create the binding. This should
         * be used if you are differing per device layouts, such as different
         * layouts for TV and Phone.
         *
         * @param fn Lambda that binds the root view.
         */
        class Bind<T : ViewBinding>(
            val fn: (View) -> T
        ) : BindingCreator<T>()
    }
}
